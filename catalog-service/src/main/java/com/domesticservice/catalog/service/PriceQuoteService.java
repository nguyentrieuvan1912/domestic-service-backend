package com.domesticservice.catalog.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.catalog.dto.CatalogResponse.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.catalog.repository.PriceQuoteSqlRepository;

@Service
public class PriceQuoteService {

    private final PriceQuoteSqlRepository repository;

    private final ObjectMapper json;

    private final CatalogQueryService catalog;

    private final ServiceHttpClient identity, booking;

    public PriceQuoteService(PriceQuoteSqlRepository repository, ObjectMapper json, CatalogQueryService catalog, @Value("${clients.identity.base-url}") String identityUrl, @Value("${clients.booking.base-url}") String bookingUrl) {
        this.repository = repository;
        this.json = json;
        this.catalog = catalog;
        identity = new ServiceHttpClient(identityUrl);
        booking = new ServiceHttpClient(bookingUrl);
    }

    static ResponseStatusException bad(String s) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
    }

    static long number(Object value) {
        try {
            long n = Long.parseLong(Objects.toString(value, ""));
            if (n < 1)
                throw bad("ID hoặc số lượng không hợp lệ.");
            return n;
        } catch (NumberFormatException ex) {
            throw bad("ID hoặc số lượng không hợp lệ.");
        }
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        if (value == null)
            return List.of();
        if (!(value instanceof List<?> l) || l.size() > 50)
            throw bad("Danh sách không hợp lệ.");
        return (List<Object>) l;
    }

    private Map<String, Object> calculate(long customer, Map<String, Object> body) {
        long serviceId = number(body.get("serviceId")), packageId = number(body.get("packageId"));
        Detail detail = catalog.detail(serviceId);
        var pack = detail.packages().stream().filter(p -> p.id() == packageId).findFirst().orElseThrow(() -> bad("Gói không thuộc dịch vụ hoặc đã ngừng hoạt động."));
        long count = body.get("requiredStaffCount") == null ? pack.defaultStaffCount() : number(body.get("requiredStaffCount"));
        if (count > 20 || count < pack.defaultStaffCount())
            throw bad("Số nhân viên không phù hợp gói dịch vụ.");
        long quantity = body.get("quantity") == null ? 1 : number(body.get("quantity"));
        if (quantity > 100 || !"ITEM".equals(detail.service().priceUnit()) && quantity != 1)
            throw bad("Số lượng không phù hợp đơn vị dịch vụ.");
        long base = Math.multiplyExact(Math.multiplyExact(pack.basePrice(), quantity), (count + pack.defaultStaffCount() - 1) / pack.defaultStaffCount()), extras = 0;
        int minutes = Math.toIntExact(pack.durationMinutes() * quantity);
        List<Map<String, Object>> adds = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Object entry : list(body.get("addOnIds"))) {
            long id = number(entry);
            if (!used.add(id))
                throw bad("Trùng lựa chọn bổ sung.");
            var add = detail.addOns().stream().filter(a -> a.id() == id).findFirst().orElseThrow(() -> bad("Lựa chọn bổ sung không thuộc dịch vụ."));
            extras = Math.addExact(extras, add.price());
            minutes += add.extraDurationMinutes();
            adds.add(Map.of("id", id, "name", add.name(), "price", add.price(), "durationMinutes", add.extraDurationMinutes()));
        }
        List<Map<String, Object>> related = new ArrayList<>();
        used.clear();
        for (Object entry : list(body.get("relatedServiceIds"))) {
            long id = number(entry);
            if (id == serviceId || !used.add(id))
                throw bad("Dịch vụ kèm theo không hợp lệ.");
            var s = catalog.detail(id).service();
            extras = Math.addExact(extras, s.basePrice());
            minutes += s.estimatedDurationMinutes();
            related.add(Map.of("id", id, "name", s.name(), "price", s.basePrice(), "durationMinutes", s.estimatedDurationMinutes()));
        }
        var answers = ServiceQuestionnaireValidator.validate(detail.requirements(), body.get("answers"));
        long discount = 0;
        Long promotionId = null;
        String code = Objects.toString(body.get("promotionCode"), "").strip().toUpperCase(Locale.ROOT);
        if (!code.isBlank()) {
            var rows = repository.calculateFindPromotions(code);
            if (rows.isEmpty())
                throw bad("Mã ưu đãi không khả dụng.");
            var p = rows.getFirst();
            promotionId = ((Number) p.get("id")).longValue();
            long total = base + extras;
            if (total < ((Number) p.get("minimum_booking_amount")).longValue())
                throw bad("Đơn chưa đạt mức tối thiểu áp dụng ưu đãi.");
            var scopes = repository.calculateFindPromotionServices(Long.class, promotionId);
            if (!scopes.isEmpty() && !scopes.contains(serviceId))
                throw bad("Ưu đãi không áp dụng cho dịch vụ này.");
            discount = "PERCENTAGE".equals(p.get("discount_type")) ? Math.multiplyExact(total, ((Number) p.get("discount_value")).longValue()) / 100 : ((Number) p.get("discount_value")).longValue();
            if (p.get("max_discount_amount") != null)
                discount = Math.min(discount, ((Number) p.get("max_discount_amount")).longValue());
            discount = Math.min(discount, total);
        }
        var configuration = body.get("configuration") instanceof Map<?, ?> config ? config : Map.of();
        Map<String, String> configLabels = Map.ofEntries(Map.entry("acType", "Loại máy lạnh"), Map.entry("acQuantity", "Số bộ máy"), Map.entry("acPosition", "Vị trí lắp đặt"), Map.entry("acNeedGas", "Yêu cầu bổ sung gas"), Map.entry("childAgeGroup", "Nhóm tuổi của bé"), Map.entry("childSpecialNotes", "Ghi chú chăm sóc bé"), Map.entry("emergencyPhone", "Điện thoại khẩn cấp"), Map.entry("elderlyAge", "Tuổi người được chăm sóc"), Map.entry("elderlyMobility", "Khả năng vận động"), Map.entry("elderlyMedicalNotes", "Ghi chú sức khỏe"), Map.entry("petType", "Loại thú cưng"), Map.entry("petWeight", "Nhóm cân nặng"), Map.entry("dietStyle", "Phong cách bữa ăn"), Map.entry("memberCount", "Số người dùng bữa"));
        if (configuration.size() > 20)
            throw bad("Quá nhiều thông tin dịch vụ.");
        for (var entry : configuration.entrySet()) {
            Object value = entry.getValue();
            if (!configLabels.containsKey(entry.getKey().toString()) || !(value instanceof String || value instanceof Boolean || value instanceof Number) || value.toString().length() > 2000)
                throw bad("Thông tin bổ sung không hợp lệ.");
        }
        if (minutes > 720)
            throw bad("Thời lượng vượt 12 giờ trong ngày. Vui lòng chọn lại số lượng hoặc gói.");
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("quantity", quantity);
        snapshot.put("configuration", configuration);
        snapshot.put("configurationLabels", configLabels);
        snapshot.put("id", serviceId);
        snapshot.put("name", detail.service().name());
        snapshot.put("image", Objects.toString(detail.service().imageUrl(), ""));
        snapshot.put("categoryCode", detail.service().categoryCode());
        snapshot.put("priceUnit", detail.service().priceUnit());
        snapshot.put("durationMinutes", detail.service().estimatedDurationMinutes());
        Map<String, String> answerLabels = new LinkedHashMap<>();
        for (var requirement : detail.requirements()) answerLabels.put(requirement.fieldKey(), requirement.label());
        snapshot.put("answerLabels", answerLabels);
        snapshot.put("packageId", packageId);
        snapshot.put("packageName", pack.name());
        snapshot.put("workflow", detail.workflow());
        snapshot.put("answers", answers);
        snapshot.put("addOns", adds);
        snapshot.put("relatedServices", related);
        snapshot.put("promotionId", promotionId);
        snapshot.put("promotionCode", code);
        snapshot.put("requiredStaffCount", count);
        snapshot.put("durationMinutes", ((minutes + 29) / 30) * 30);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customer);
        result.put("serviceId", serviceId);
        result.put("packageId", packageId);
        result.put("baseAmount", base);
        result.put("addOnAmount", extras);
        result.put("discountAmount", discount);
        result.put("totalAmount", base + extras - discount);
        result.put("snapshot", snapshot);
        result.put("durationMinutes", snapshot.get("durationMinutes"));
        result.put("promotionId", promotionId);
        result.put("promotionCode", code);
        return result;
    }

    public Map<String, Object> preview(String token, Map<String, Object> body) {
        return calculate(ServiceHttpClient.userId(identity.authenticate(token, "CUSTOMER")), body);
    }

    @Transactional
    public Map<String, Object> create(String token, Map<String, Object> body) {
        long user = ServiceHttpClient.userId(identity.authenticate(token, "CUSTOMER"));
        String key = Objects.toString(body.get("requestKey"), "");
        if (key.isBlank() || key.length() > 160)
            throw bad("Thiếu mã chống tạo trùng yêu cầu.");
        String hash = hash(json.writeValueAsString(body));
        repository.createLockRecords(user);
        var prior = repository.createFindPriceQuotes(user, key);
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("request_hash")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng với nội dung khác.");
            return view(prior.getFirst());
        }
        var quote = calculate(user, body);
        var row = repository.createFindPriceQuotes2(user, quote.get("serviceId"), quote.get("packageId"), json.writeValueAsString(quote.get("snapshot")), quote.get("baseAmount"), quote.get("addOnAmount"), quote.get("discountAmount"), quote.get("totalAmount"), key, hash);
        return view(row);
    }

    Map<String, Object> view(Map<String, Object> row) {
        var snapshot = json.readValue(row.get("snapshot").toString(), new TypeReference<Map<String, Object>>() {
        });
        var result = new LinkedHashMap<String, Object>();
        result.put("id", row.get("id"));
        result.put("customerId", row.get("customer_id"));
        result.put("serviceId", row.get("service_id"));
        result.put("packageId", row.get("package_id"));
        result.put("baseAmount", row.get("base_amount"));
        result.put("addOnAmount", row.get("add_on_amount"));
        result.put("discountAmount", row.get("discount_amount"));
        result.put("totalAmount", row.get("total_amount"));
        result.put("expiresAt", ((Timestamp) row.get("expires_at")).toInstant().toString());
        result.put("snapshot", snapshot);
        result.put("durationMinutes", snapshot.get("durationMinutes"));
        return result;
    }

    @Transactional
    public Map<String, Object> consume(String token, long id, long bookingId) {
        var user = identity.authenticate(token, "CUSTOMER", "ADMIN");
        var book = booking.get("/api/v1/booking/bookings/" + bookingId, token);
        var rows = repository.consumeFindPriceQuotesForUpdate(id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo giá.");
        var q = rows.getFirst();
        if (!Objects.equals(((Number) book.get("customerId")).longValue(), ((Number) q.get("customer_id")).longValue()) || !(book.get("quoteId") instanceof Number quoteId) || !Objects.equals(quoteId.longValue(), id) || !Objects.equals(((Number) book.get("totalAmount")).longValue(), ((Number) q.get("total_amount")).longValue()))
            throw bad("Đơn không khớp báo giá.");
        if (!"ADMIN".equals(user.get("role")) && ServiceHttpClient.userId(user) != ((Number) q.get("customer_id")).longValue())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Báo giá không thuộc bạn.");
        if (q.get("consumed_booking_id") != null) {
            if (((Number) q.get("consumed_booking_id")).longValue() != bookingId)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Báo giá đã dùng cho đơn khác.");
            return Map.of("id", id, "status", "CONSUMED");
        }
        if (((Timestamp) q.get("expires_at")).toInstant().isBefore(Instant.now()) || "CANCELLED".equals(book.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Báo giá hết hạn hoặc đơn đã hủy.");
        var snapshot = json.readValue(q.get("snapshot").toString(), new TypeReference<Map<String, Object>>() {
        });
        if (snapshot.get("promotionId") != null) {
            int changed = repository.consumeUpdatePromotions(snapshot.get("promotionId"));
            if (changed == 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ưu đãi đã hết lượt hoặc ngừng áp dụng.");
        }
        repository.consumeUpdatePriceQuotes(bookingId, id);
        return Map.of("id", id, "status", "CONSUMED");
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
