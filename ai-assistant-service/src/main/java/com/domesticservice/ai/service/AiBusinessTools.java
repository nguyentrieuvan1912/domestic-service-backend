package com.domesticservice.ai.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class AiBusinessTools {
    private static final Map<String, Object> INTEGER = Map.of("type", "integer", "minimum", 1);
    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final Map<String, Object> IDS = Map.of("type", "array", "items", INTEGER);
    private static final List<Map<String, Object>> DECLARATIONS = List.of(
        tool("search_services", "Tìm dịch vụ đang hoạt động theo từ khóa ngắn.", Map.of("query", STRING), "query"),
        tool("get_catalog_overview", "Đọc toàn bộ nhóm dịch vụ, dịch vụ và tiện ích bổ sung đang hoạt động.", Map.of()),
        tool("recommend_services", "Gợi ý dịch vụ và gói theo ngữ nghĩa nhu cầu tiếng Việt. Lọc định mức diện tích, số nhân viên và thời lượng trước khi xếp hạng.",
            Map.of("query", STRING, "areaM2", Map.of("type", "number", "exclusiveMinimum", 0),
                "maxStaffCount", INTEGER, "maxDurationMinutes", INTEGER), "query"),
        tool("search_add_ons", "Tìm tiện ích bổ sung và dịch vụ sở hữu theo tên hoặc từ khóa.", Map.of("query", STRING), "query"),
        tool("get_service_details", "Đọc gói, tiện ích và các yêu cầu cần hỏi của dịch vụ.", Map.of("serviceId", INTEGER), "serviceId"),
        tool("recommend_packages_by_area", "Gợi ý gói theo tổng diện tích m² từ Catalog. Dùng serviceId đã tìm được, không dùng để báo giá cuối.",
            Map.of("serviceId", INTEGER, "areaM2", Map.of("type", "number", "exclusiveMinimum", 0)), "serviceId", "areaM2"),
        tool("get_current_promotions", "Đọc ưu đãi công khai đang có và ưu đãi riêng của khách.", Map.of()),
        tool("get_customer_addresses", "Liệt kê ID và phường/xã của địa chỉ đã lưu của khách.", Map.of()),
        tool("get_my_booking", "Tra trạng thái đơn của khách bằng ID số.", Map.of("bookingId", INTEGER), "bookingId"),
        tool("get_my_bookings", "Xem các đơn gần đây của khách hoặc tìm theo mã đơn chính xác.",
            Map.of("reference", STRING)),
        tool("check_staff_availability", "Đếm nhân viên phù hợp tại địa chỉ và giờ cụ thể. Không giữ chỗ.",
            Map.of("serviceId", INTEGER, "addressId", INTEGER, "startsAt", STRING, "durationMinutes", INTEGER),
            "serviceId", "addressId", "startsAt", "durationMinutes"),
        tool("get_price_quote", "Tính giá xem trước. Dùng fieldKey và giá trị SELECT đúng nguyên văn từ get_service_details, chỉ gửi câu trả lời khách đã cung cấp. Với combo, quantity là số combo, không phải số thiết bị trong combo.",
            Map.of("serviceId", INTEGER, "packageId", INTEGER, "quantity", INTEGER, "requiredStaffCount", INTEGER,
                "promotionCode", STRING, "addOnIds", IDS, "relatedServiceIds", IDS,
                "answers", Map.of("type", "object", "additionalProperties", true)),
            "serviceId", "packageId"));

    private final ServiceHttpClient catalog;
    private final ServiceHttpClient identity;
    private final ServiceHttpClient booking;
    private final ObjectMapper json;
    private final CatalogSemanticRecommendations recommendations;

    AiBusinessTools(@Value("${clients.catalog.base-url}") String catalogUrl,
                    @Value("${clients.identity.base-url}") String identityUrl,
                    @Value("${clients.booking.base-url}") String bookingUrl, ObjectMapper json,
                    CatalogSemanticRecommendations recommendations) {
        catalog = new ServiceHttpClient(catalogUrl);
        identity = new ServiceHttpClient(identityUrl);
        booking = new ServiceHttpClient(bookingUrl);
        this.json = json;
        this.recommendations = recommendations;
    }

    static Map<String, Object> tool(String name, String description, Map<String, Object> fields, String... required) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters",
            Map.of("type", "object", "properties", fields, "required", List.of(required), "additionalProperties", false)));
    }

    List<Map<String, Object>> declarations() {
        return DECLARATIONS;
    }

    List<Map<String, Object>> activeServices() {
        return activeServices(catalog);
    }

    static List<Map<String, Object>> activeServices(ServiceHttpClient catalog) {
        var services = new ArrayList<Map<String, Object>>();
        int page = 0;
        int totalPages;
        do {
            var result = catalog.get("/api/v1/catalog/services?size=100&page=" + page, null);
            services.addAll(rows(result, "items"));
            totalPages = result.get("totalPages") instanceof Number count ? count.intValue() : 1;
            page++;
        } while (page < totalPages);
        return services;
    }

    List<Map<String, Object>> directory() {
        return activeServices().stream()
            .map(service -> fields(service, "id", "name", "categoryName")).toList();
    }

    Map<String, Object> call(String name, JsonNode args, String token) {
        if (!args.isObject() || args.toString().length() > 6000) throw new IllegalArgumentException();
        return switch (name) {
            case "search_services" -> searchServices(args);
            case "get_catalog_overview" -> catalogOverview();
            case "recommend_services" -> json.convertValue(recommendations.recommend(
                json.convertValue(args, CatalogSemanticRecommendations.Query.class)), new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
            case "search_add_ons" -> searchAddOns(args);
            case "recommend_packages_by_area" -> recommendPackages(args);
            case "get_service_details" -> catalog.get("/api/v1/catalog/services/" + id(args, "serviceId"), null);
            case "get_current_promotions" -> currentPromotions(token);
            case "get_customer_addresses" -> Map.of("addresses", identity.list("/api/v1/identity/customer/addresses", token)
                .stream().limit(15).map(item -> fields(item, "id", "ward", "district", "isDefault")).toList());
            case "get_my_booking" -> fields(booking.get("/api/v1/booking/bookings/" + id(args, "bookingId"), token),
                "id", "bookingCode", "status", "paymentStatus", "startsAt", "totalAmount");
            case "get_my_bookings" -> recentBookings(args.path("reference").asText(""), token);
            case "check_staff_availability" -> availability(args, token);
            case "get_price_quote" -> quote(args, token);
            default -> throw new IllegalArgumentException();
        };
    }

    private Map<String, Object> currentPromotions(String token) {
        var publicPromotions = catalog.list("/api/v1/catalog/promotions", null).stream()
            .filter(item -> Boolean.TRUE.equals(item.get("isActive"))).limit(12).toList();
        try {
            Instant now = Instant.now();
            var personal = catalog.list("/api/v1/catalog/customer/vouchers", token).stream()
                .filter(item -> item.get("used_count") instanceof Number n && n.intValue() == 0
                    && isBeforeExpiry(item.get("expires_at"), now))
                .limit(12).map(item -> fields(item, "code", "name", "discount", "expires_at")).toList();
            return Map.of("public", publicPromotions, "personal", personal);
        } catch (ResponseStatusException failure) {
            return Map.of("public", publicPromotions, "personal", List.of(), "personalUnavailable", true);
        }
    }

    private static boolean isBeforeExpiry(Object expiry, Instant now) {
        try {
            return Instant.parse(Objects.toString(expiry, "")).isAfter(now);
        } catch (DateTimeParseException invalidExpiry) {
            return false;
        }
    }

    private Map<String, Object> recentBookings(String reference, String token) {
        if (reference.length() > 50) throw new IllegalArgumentException();
        var rows = booking.list("/api/v1/booking/bookings", token).stream()
            .filter(row -> reference.isBlank() || reference.equalsIgnoreCase(Objects.toString(row.get("bookingCode"), "")))
            .limit(5).map(row -> {
                var summary = fields(row, "id", "bookingCode", "status", "paymentStatus", "bookingDate", "startTime");
                if (row.get("service") instanceof Map<?, ?> service && service.get("name") instanceof String name) {
                    summary.put("serviceName", name);
                }
                return summary;
            }).toList();
        return Map.of("items", rows);
    }

    private Map<String, Object> searchServices(JsonNode args) {
        var terms = searchTerms(args);
        var services = activeServices();
        var matched = services.stream().filter(service -> matchesTerms(terms,
            service.getOrDefault("name", "") + " " + service.getOrDefault("categoryName", ""))).toList();
        if (matched.isEmpty()) {
            matched = services.stream().filter(service -> matchesTerms(terms,
                service.getOrDefault("name", "") + " " + service.getOrDefault("categoryName", "") + " "
                    + service.getOrDefault("shortDescription", "") + " " + service.getOrDefault("description", ""))).toList();
        }
        var matches = matched.stream()
            .map(service -> fields(service, "id", "name", "categoryName", "description", "priceUnit", "serviceType")).toList();
        return Map.of("items", matches, "total", matches.size(),
            "message", matches.isEmpty() ? "Chưa tìm thấy dịch vụ khớp từ khóa. Dùng get_catalog_overview để đối chiếu danh mục đầy đủ." : "");
    }

    private static boolean matchesTerms(List<String> terms, String text) {
        String normalized = " " + catalogWords(text) + " ";
        return terms.stream().allMatch(term -> normalized.contains(" " + term + " "));
    }

    private static List<String> searchTerms(JsonNode args) {
        String query = catalogWords(searchQuery(args))
            .replace("dieu hoa", "may lanh")
            .replace("mua do an", "di cho ho")
            .replace("mua thuc pham", "di cho ho")
            .replace("trong tre", "cham soc tre")
            .replace("giu tre", "cham soc tre");
        var ignored = Set.of("co", "dich", "vu", "khong", "ko", "hien", "tai", "he", "thong",
            "toi", "muon", "biet", "nao", "nhung", "gi", "cac", "ban", "hay", "giup", "tim", "kiem", "duoc");
        return Arrays.stream(query.split(" ")).filter(term -> !ignored.contains(term)).distinct().toList();
    }

    private Map<String, Object> searchAddOns(JsonNode args) {
        var terms = searchTerms(args);
        var matches = new ArrayList<Map<String, Object>>();
        for (var service : rows(catalogOverview(), "services")) {
            for (var addOn : rows(service, "addOns")) {
                String name = catalogWords(Objects.toString(addOn.get("name"), ""));
                if (terms.stream().allMatch(term -> (" " + name + " ").contains(" " + term + " "))) {
                    var match = new LinkedHashMap<>(addOn);
                    match.put("serviceId", service.get("id"));
                    match.put("serviceName", service.get("name"));
                    matches.add(match);
                }
            }
        }
        return Map.of("items", matches, "total", matches.size());
    }

    private Map<String, Object> catalogOverview() {
        var services = new ArrayList<Map<String, Object>>();
        for (var service : activeServices()) {
            var detail = catalog.get("/api/v1/catalog/services/" + service.get("id"), null);
            var entry = new LinkedHashMap<>(fields(service, "id", "name", "categoryId", "categoryName"));
            entry.put("addOns", rows(detail, "addOns").stream()
                .map(addOn -> fields(addOn, "id", "name", "description")).toList());
            services.add(entry);
        }
        var categories = catalog.list("/api/v1/catalog/categories", null).stream()
            .map(category -> fields(category, "id", "name", "code", "group")).toList();
        return Map.of("categories", categories, "services", services, "total", services.size());
    }

    static String catalogWords(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ', 'd')
            .replaceAll("[^a-z0-9]+", " ").strip();
    }

    private Map<String, Object> recommendPackages(JsonNode args) {
        var area = args.path("areaM2");
        if (!area.isNumber()) throw new IllegalArgumentException();
        BigDecimal amount = area.decimalValue();
        if (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(100000)) > 0 || amount.scale() > 4) {
            throw new IllegalArgumentException();
        }
        var result = catalog.get("/api/v1/catalog/services/" + id(args, "serviceId")
            + "/recommendation?areaM2=" + amount.toPlainString(), null);
        return Map.of("status", result.get("status"), "areaM2", amount, "message", result.get("message"),
            "options", rows(result, "options").stream().limit(3)
                .map(option -> fields(option, "packageId", "packageName", "requiredStaffCount",
                    "quantity", "durationMinutes", "coveredAreaM2")).toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return (List<Map<String, Object>>) result.getOrDefault(key, List.of());
    }

    private Map<String, Object> availability(JsonNode args, String token) {
        long duration = id(args, "durationMinutes");
        if (duration > 720) throw new IllegalArgumentException();
        var start = OffsetDateTime.parse(requiredText(args, "startsAt", 40)).toInstant();
        var staff = booking.list("/api/v1/booking/customer/staff?serviceId=" + id(args, "serviceId")
            + "&addressId=" + id(args, "addressId") + "&startsAt=" + start.toString()
            + "&durationMinutes=" + duration, token);
        return Map.of("availableStaffCount", staff.size(), "startsAt", start.toString(), "holdsSlot", false);
    }

    private Map<String, Object> quote(JsonNode args, String token) {
        var request = new LinkedHashMap<String, Object>();
        long serviceId = id(args, "serviceId");
        long packageId = id(args, "packageId");
        request.put("serviceId", serviceId);
        request.put("packageId", packageId);
        for (String name : List.of("quantity", "requiredStaffCount")) {
            if (args.has(name)) request.put(name, id(args, name));
        }
        if (args.has("promotionCode")) request.put("promotionCode", requiredText(args, "promotionCode", 60));
        for (String name : List.of("addOnIds", "relatedServiceIds")) {
            if (!args.has(name)) continue;
            JsonNode values = args.path(name);
            if (!values.isArray() || values.size() > 20) throw new IllegalArgumentException();
            var ids = new ArrayList<Long>();
            for (var value : values) ids.add(positive(value));
            request.put(name, ids);
        }
        request.put("answers", quoteAnswers(serviceId, packageId, args.path("answers")));
        @SuppressWarnings("unchecked")
        var response = (Map<String, Object>) catalog.call("POST", "/api/v1/catalog/quote-preview", token, request);
        return fields(response, "serviceId", "packageId", "baseAmount", "addOnAmount", "discountAmount", "totalAmount", "durationMinutes");
    }

    private Map<String, Object> quoteAnswers(long serviceId, long packageId, JsonNode supplied) {
        if (!supplied.isMissingNode() && !supplied.isObject()) throw new IllegalArgumentException();
        var detail = catalog.get("/api/v1/catalog/services/" + serviceId, null);
        if (rows(detail, "packages").stream().noneMatch(pack ->
            pack.get("id") instanceof Number id && id.longValue() == packageId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gói đã chọn không thuộc dịch vụ. Hãy xác nhận lại gói với khách.");
        }
        var answers = new LinkedHashMap<String, Object>();
        var unresolved = new ArrayList<String>();
        for (var field : rows(detail, "requirements")) {
            String key = field.get("fieldKey").toString();
            var answer = supplied.path(key);
            var options = field.get("options") instanceof List<?> values ? values : List.of();
            String label = Objects.toString(field.get("label"), key);
            if (answer.isMissingNode() || answer.isNull() || answer.isTextual() && answer.asText().isBlank()) {
                if (Boolean.TRUE.equals(field.get("required"))) unresolved.add(label + (options.isEmpty() ? "" : " " + options));
            } else if ("SELECT".equals(field.get("fieldType")) && !options.isEmpty()) {
                var canonical = options.stream().filter(option -> option.toString().equalsIgnoreCase(answer.asText().strip()))
                    .findFirst();
                if (canonical.isPresent()) answers.put(key, canonical.get());
                else unresolved.add(label + " " + options);
            } else {
                answers.put(key, json.convertValue(answer, Object.class));
            }
        }
        if (!unresolved.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cần xác nhận trước khi báo giá: " + String.join("; ", unresolved));
        }
        return answers;
    }

    private static Map<String, Object> fields(Map<String, Object> source, String... names) {
        var result = new LinkedHashMap<String, Object>();
        for (String name : names) if (source.get(name) != null) result.put(name, source.get(name));
        return result;
    }

    private static long id(JsonNode args, String name) {
        return positive(args.path(name));
    }

    private static long positive(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 1) throw new IllegalArgumentException();
        return node.longValue();
    }

    private static String requiredText(JsonNode args, String name, int limit) {
        var node = args.path(name);
        String value = node.asText("").strip();
        if (!node.isTextual() || value.isBlank() || value.length() > limit) throw new IllegalArgumentException();
        return value;
    }

    private static String searchQuery(JsonNode args) {
        String query = requiredText(args, "query", 120).replaceAll("[^\\p{L}\\p{M}\\p{N} -]", " ").strip();
        if (query.isBlank()) throw new IllegalArgumentException();
        return query;
    }
}
