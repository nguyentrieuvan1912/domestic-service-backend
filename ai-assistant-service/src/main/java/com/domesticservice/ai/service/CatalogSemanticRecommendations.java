package com.domesticservice.ai.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CatalogSemanticRecommendations {
    private static final Pattern AREA = Pattern.compile("(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*m(?:2|²)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile(
        "(?:tối đa|không quá|chỉ có|trong|dưới)\\s+(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*(?:giờ|tiếng)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STAFF = Pattern.compile(
        "(?:tối đa|không quá|chỉ(?: cần| có)?|giới hạn)\\s+(\\d{1,2})\\s*(?:nhân viên|người làm|người dọn)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final JdbcTemplate db;
    private final OllamaEmbeddings embeddings;
    private final ObjectMapper json;
    private final ServiceHttpClient catalog;
    private final boolean enabled;

    CatalogSemanticRecommendations(JdbcTemplate db, OllamaEmbeddings embeddings, ObjectMapper json,
                                   @Value("${clients.catalog.base-url}") String catalogUrl,
                                   @Value("${ai.knowledge.indexer.enabled:false}") boolean enabled) {
        this.db = db;
        this.embeddings = embeddings;
        this.json = json;
        catalog = new ServiceHttpClient(catalogUrl);
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    void warm() {
        if (!enabled) return;
        try { vectors(snapshot()); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Catalog semantic index will retry on its next refresh.");
        }
    }

    public Result recommend(Query request) {
        String query = Objects.toString(request.query(), "").strip();
        if (query.isBlank() || query.length() > 2000) throw bad("Mô tả nhu cầu từ 1 đến 2.000 ký tự.");
        BigDecimal area = request.areaM2();
        if (area == null) {
            var match = AREA.matcher(query);
            if (match.find()) area = new BigDecimal(match.group(1).replace(',', '.'));
        }
        var staffMatch = STAFF.matcher(query);
        var durationMatch = DURATION.matcher(query);
        int staff = request.maxStaffCount() == null ? 20 : request.maxStaffCount();
        if (request.maxStaffCount() == null && staffMatch.find()) staff = Integer.parseInt(staffMatch.group(1));
        int minutes = request.maxDurationMinutes() == null ? 720 : request.maxDurationMinutes();
        if (request.maxDurationMinutes() == null && durationMatch.find()) {
            minutes = new BigDecimal(durationMatch.group(1).replace(',', '.')).multiply(BigDecimal.valueOf(60)).intValue();
        }
        if (staff < 1 || staff > 20 || minutes < 1 || minutes > 720
            || area != null && (area.signum() <= 0 || area.compareTo(BigDecimal.valueOf(100000)) > 0)) {
            throw bad("Diện tích hoặc giới hạn nhân viên/thời lượng không hợp lệ.");
        }
        if (requestWords(query).isEmpty()) return new Result("NEEDS_DETAILS", embeddings.model,
            "Bạn mô tả rõ công việc cần làm, đồ vật hoặc khu vực cần hỗ trợ để mình tìm dịch vụ phù hợp nhé.", List.of(), area);
        return rank(query, area, staff, minutes, false);
    }

    private Result rank(String query, BigDecimal area, int staff, int minutes, boolean retried) {
        var entries = snapshot();
        var vectors = vectors(entries);
        var queryVector = embeddings.query(query,
            "Retrieve household services and service packages that satisfy the customer's described needs.");
        var candidates = new ArrayList<Candidate>();
        for (var entry : entries) {
            var eligible = eligible(entry, area, staff, minutes);
            if (eligible.isEmpty()) continue;
            var vector = vectors.get(key("SERVICE", id(entry.service()), serviceText(entry)));
            if (vector == null) continue;
            double similarity = cosine(queryVector, vector.vector());
            double score = 0.85 * similarity + 0.15 * lexical(query, entry.service());
            var packageOptions = new ArrayList<PackageOption>();
            boolean needsArea = area == null && hasArea(entry);
            if (!needsArea) {
                for (var option : eligible) {
                    var pack = entry.packages().stream().filter(item -> id(item) == option.packageId()).findFirst().orElseThrow();
                    var packVector = vectors.get(key("PACKAGE", id(pack), packageText(entry, pack)));
                    if (packVector == null) continue;
                    String reason = area != null && hasArea(entry)
                        ? "Đủ định mức cho tổng diện tích " + area.stripTrailingZeros().toPlainString() + " m² theo Catalog."
                        : Objects.toString(pack.get("description"), "Gói thuộc dịch vụ đã chọn.");
                    packageOptions.add(new PackageOption(option.packageId(), pack.get("name").toString(), option.quantity(),
                        option.staffCount(), option.durationMinutes(), cosine(queryVector, packVector.vector()),
                        reason, packVector.hash(), packVector.id()));
                }
                packageOptions.sort(Comparator.comparingDouble(PackageOption::similarity).reversed()
                    .thenComparingInt(PackageOption::staffCount).thenComparingLong(PackageOption::packageId));
            }
            candidates.add(new Candidate(id(entry.service()), entry.service().get("name").toString(),
                Objects.toString(entry.service().get("serviceType"), ""), similarity, score,
                "Phù hợp nội dung nhu cầu và mô tả dịch vụ: "
                    + Objects.toString(entry.service().getOrDefault("shortDescription", entry.service().get("description")), ""),
                hasArea(entry), needsArea, packageOptions.stream().limit(3).toList(), vector.hash(), vector.id()));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(Candidate::serviceId));
        var types = new LinkedHashMap<String, Candidate>();
        for (var candidate : candidates) types.putIfAbsent(candidate.serviceType(), candidate);
        double best = candidates.isEmpty() ? 0 : candidates.getFirst().score();
        var ranked = types.values().stream().filter(candidate -> candidate.similarity() >= 0.45
            && best - candidate.score() <= 0.10).limit(3).toList();
        if (ranked.isEmpty()) {
            boolean constrained = area != null || staff < 20 || minutes < 720;
            return new Result(constrained ? "NO_FIT" : "NEEDS_DETAILS", embeddings.model,
                constrained ? "Chưa tìm được gói phù hợp trong giới hạn diện tích, nhân viên hoặc thời lượng đã chọn. Bạn kiểm tra lại hoặc điều chỉnh giới hạn nhé."
                    : "Bạn mô tả rõ công việc cần làm, đồ vật hoặc khu vực cần hỗ trợ để mình tìm dịch vụ phù hợp nhé.", List.of(), area);
        }
        for (var candidate : ranked) {
            Entry fresh;
            try { fresh = detail(candidate.serviceId()); }
            catch (ResponseStatusException missing) {
                if (missing.getStatusCode().value() != 404) throw missing;
                return retried ? changed(area) : rank(query, area, staff, minutes, true);
            }
            var allowed = eligible(fresh, area, staff, minutes);
            if (hasArea(fresh) != candidate.areaBased()
                || !KnowledgeDocuments.hash(serviceText(fresh)).equals(candidate.sourceHash())
                || candidate.packages().stream().anyMatch(option -> fresh.packages().stream().noneMatch(pack ->
                    id(pack) == option.packageId() && KnowledgeDocuments.hash(packageText(fresh, pack)).equals(option.sourceHash()))
                    || allowed.stream().noneMatch(pack -> pack.packageId() == option.packageId()
                        && pack.quantity() == option.quantity() && pack.staffCount() == option.staffCount()
                        && pack.durationMinutes() == option.durationMinutes()))) {
                return retried ? changed(area) : rank(query, area, staff, minutes, true);
            }
        }
        boolean uncertain = ranked.getFirst().similarity() < 0.52
            || ranked.size() > 1 && ranked.getFirst().score() - ranked.get(1).score() < 0.025;
        return new Result(uncertain ? "CLARIFY" : "RECOMMENDED", embeddings.model,
            uncertain ? "Có vài dịch vụ gần với nhu cầu. Bạn chọn công việc chính hoặc mô tả thêm để mình tư vấn đúng nhé."
                : "Các gợi ý lấy từ danh mục hiện tại. Bạn có thể xem dịch vụ hoặc chọn gói để bổ sung lịch và địa chỉ.",
            ranked, area);
    }

    private Result changed(BigDecimal area) {
        return new Result("CATALOG_CHANGED", embeddings.model, "Danh mục vừa thay đổi. Bạn thử lại để mình đối chiếu gói mới nhé.", List.of(), area);
    }

    private List<Eligible> eligible(Entry entry, BigDecimal area, int staff, int minutes) {
        if (area != null && hasArea(entry)) {
            Map<String, Object> response;
            try {
                response = catalog.get("/api/v1/catalog/services/" + id(entry.service()) + "/recommendation?areaM2="
                    + area.toPlainString() + "&maxStaffCount=" + staff + "&maxDurationMinutes=" + minutes, null);
            } catch (ResponseStatusException failure) {
                if (failure.getStatusCode().value() == 400) return List.of();
                throw failure;
            }
            return rows(response, "options").stream().map(option -> new Eligible(
                number(option, "packageId"), (int) number(option, "quantity"),
                (int) number(option, "requiredStaffCount"), (int) number(option, "durationMinutes"))).toList();
        }
        return entry.packages().stream()
            .filter(pack -> number(pack, "defaultStaffCount") <= staff
                && ((number(pack, "durationMinutes") + 29) / 30) * 30 <= minutes)
            .map(pack -> new Eligible(id(pack), 1, (int) number(pack, "defaultStaffCount"),
                (int) (((number(pack, "durationMinutes") + 29) / 30) * 30))).toList();
    }

    private static boolean hasArea(Entry entry) {
        return entry.requirements().stream().anyMatch(field -> "areaM2".equals(field.get("fieldKey")) && "NUMBER".equals(field.get("fieldType")));
    }

    private List<Entry> snapshot() {
        var entries = new ArrayList<Entry>();
        for (var service : AiBusinessTools.activeServices(catalog)) {
            try { entries.add(detail(id(service))); }
            catch (ResponseStatusException failure) { if (failure.getStatusCode().value() != 404) throw failure; }
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private Entry detail(long id) {
        var detail = catalog.get("/api/v1/catalog/services/" + id, null);
        return new Entry((Map<String, Object>) detail.get("service"), rows(detail, "packages"), rows(detail, "requirements"));
    }

    private synchronized Map<String, Vector> vectors(List<Entry> entries) {
        var sources = new ArrayList<Source>();
        for (var entry : entries) {
            sources.add(new Source("SERVICE", id(entry.service()), id(entry.service()), serviceText(entry)));
            for (var pack : entry.packages()) sources.add(new Source("PACKAGE", id(pack), id(entry.service()), packageText(entry, pack)));
        }
        var result = new HashMap<String, Vector>();
        var pending = new ArrayList<Source>();
        for (var source : sources) {
            var cached = db.queryForList("""
                SELECT id,embedding::text FROM catalog_semantic_vectors
                WHERE resource_type=? AND resource_id=? AND content_hash=? AND embedding_model=?
                """, source.type(), source.id(), KnowledgeDocuments.hash(source.text()), embeddings.model);
            if (cached.isEmpty()) pending.add(source);
            else result.put(key(source.type(), source.id(), source.text()), vector(cached.getFirst(), source.text()));
        }
        for (int offset = 0; offset < pending.size(); offset += 8) {
            var batch = pending.subList(offset, Math.min(offset + 8, pending.size()));
            var generated = embeddings.documents(batch.stream().map(Source::text).toList());
            for (int index = 0; index < batch.size(); index++) {
                var source = batch.get(index);
                String hash = KnowledgeDocuments.hash(source.text());
                db.update("""
                    INSERT INTO catalog_semantic_vectors(resource_type,resource_id,service_id,content_hash,embedding_model,dimension,content,embedding)
                    VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING
                    """, source.type(), source.id(), source.serviceId(), hash, embeddings.model, generated.get(index).size(),
                    source.text(), json.writeValueAsString(generated.get(index)));
                var stored = db.queryForMap("""
                    SELECT id,embedding::text FROM catalog_semantic_vectors
                    WHERE resource_type=? AND resource_id=? AND content_hash=? AND embedding_model=?
                    """, source.type(), source.id(), hash, embeddings.model);
                result.put(key(source.type(), source.id(), source.text()), vector(stored, source.text()));
            }
        }
        return result;
    }

    private Vector vector(Map<String, Object> row, String content) {
        return new Vector(number(row, "id"), KnowledgeDocuments.hash(content),
            json.readValue(row.get("embedding").toString(), new TypeReference<List<Double>>() {}));
    }
    private static String key(String type, long id, String text) { return type + ":" + id + ":" + KnowledgeDocuments.hash(text); }
    private static String serviceText(Entry entry) {
        var service = entry.service();
        return clip("Dịch vụ " + service.get("name") + "\nNhóm " + service.get("categoryName") + "\n"
            + Objects.toString(service.get("description"), "") + "\n"
            + Objects.toString(service.get("shortDescription"), "") + "\n" + Objects.toString(service.get("highlights"), ""), 2400);
    }
    private static String packageText(Entry entry, Map<String, Object> pack) {
        return clip("Dịch vụ " + entry.service().get("name") + "\nGói " + pack.get("name") + "\n"
            + Objects.toString(pack.get("description"), "") + "\n"
            + clip(Objects.toString(entry.service().get("description"), ""), 600)
            + "\nThời lượng " + pack.get("durationMinutes") + " phút; nhân viên " + pack.get("defaultStaffCount")
            + "; định mức diện tích " + pack.get("maxArea"), 2400);
    }
    private static String clip(String text, int limit) {
        if (text.length() <= limit) return text;
        int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end);
    }
    private static double cosine(List<Double> first, List<Double> second) {
        if (first.size() != second.size()) return 0;
        double score = 0;
        for (int index = 0; index < first.size(); index++) score += first.get(index) * second.get(index);
        return score;
    }
    private static double lexical(String query, Map<String, Object> service) {
        var words = requestWords(query);
        if (words.isEmpty()) return 0;
        String title = " " + AiBusinessTools.catalogWords(service.get("name") + " " + service.get("categoryName")) + " ";
        return words.stream().filter(word -> title.contains(" " + word + " ")).count() / (double) words.size();
    }
    private static Set<String> requestWords(String query) {
        var words = new HashSet<>(Arrays.asList(AiBusinessTools.catalogWords(query).split(" ")));
        words.removeAll(Set.of("toi", "minh", "muon", "can", "dich", "vu", "nao", "gi", "co", "khong", "nha",
            "va", "cho", "lam", "giup", "nguoi", "ho", "tro", "do", "duoc", "a", "tu", "van",
            "goi", "y", "de", "xuat", "nen", "chon", "phu", "hop", "voi", "nhu", "cau"));
        return words;
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.getOrDefault(key, List.of());
    }
    private static long id(Map<String, Object> row) { return number(row, "id"); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    public record Query(String query, BigDecimal areaM2, Integer maxStaffCount, Integer maxDurationMinutes) {}
    public record Result(String status, String embeddingModel, String message, List<Candidate> candidates, BigDecimal areaM2) {}
    public record Candidate(long serviceId, String name, String serviceType, double similarity, double score, String reason,
                            boolean areaBased, boolean needsArea, List<PackageOption> packages, String sourceHash, long embeddingId) {}
    public record PackageOption(long packageId, String name, int quantity, int staffCount, int durationMinutes,
                                double similarity, String reason, String sourceHash, long embeddingId) {}
    private record Eligible(long packageId, int quantity, int staffCount, int durationMinutes) {}
    private record Entry(Map<String, Object> service, List<Map<String, Object>> packages, List<Map<String, Object>> requirements) {}
    private record Source(String type, long id, long serviceId, String text) {}
    private record Vector(long id, String hash, List<Double> vector) {}
}
