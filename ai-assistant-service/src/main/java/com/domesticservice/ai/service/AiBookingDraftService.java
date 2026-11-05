package com.domesticservice.ai.service;

import com.domesticservice.ai.entity.BookingDraft;
import com.domesticservice.ai.repository.AiConversationSqlRepository;
import com.domesticservice.ai.repository.BookingDraftRepository;
import com.domesticservice.common.web.ServiceHttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiBookingDraftService {
    private static final Set<String> FIELDS = Set.of("serviceId", "packageId", "mode", "quantity",
        "startsAt", "addressId", "requiredStaffCount", "addOnIds", "promotionCode", "answers");
    private static final List<String> REQUIRED = List.of("serviceId", "packageId", "mode", "quantity",
        "startsAt", "addressId", "requiredStaffCount");
    private static final ZoneId CUSTOMER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final BookingDraftRepository drafts;
    private final AiConversationSqlRepository conversations;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final ServiceHttpClient identity;
    private final ServiceHttpClient catalog;

    public AiBookingDraftService(BookingDraftRepository drafts, AiConversationSqlRepository conversations,
                                 PlatformTransactionManager manager, ObjectMapper json,
                                 @Value("${clients.identity.base-url}") String identityUrl,
                                 @Value("${clients.catalog.base-url}") String catalogUrl) {
        this.drafts = drafts;
        this.conversations = conversations;
        this.transactions = new TransactionTemplate(manager);
        this.json = json;
        identity = new ServiceHttpClient(identityUrl);
        catalog = new ServiceHttpClient(catalogUrl);
    }

    public Map<String, Object> get(String token, long conversation) {
        long customer = actor(token);
        requireOwner(conversation, customer, false);
        var draft = current(conversation, customer);
        var data = data(draft);
        var choices = choices(data, token);
        return response(draft, data, missing(data, choices), choices);
    }

    public Map<String, Object> patch(String token, long conversation, Patch patch) {
        long customer = actor(token);
        requireOwner(conversation, customer, false);
        var prepared = prepare(token, conversation, customer, patch, false);
        return transactions.execute(status -> save(conversation, customer, prepared));
    }

    Patch snapshot(long conversation, long customer) {
        var draft = current(conversation, customer);
        return new Patch(draft == null ? null : draft.getId(), draft == null ? null : draft.getVersion(), data(draft));
    }

    record Prepared(Patch patch, Map<String, Object> data, List<String> missing, Choices choices) {}

    Prepared prepare(String token, long conversation, long customer, Patch patch, boolean fromChat) {
        if (patch.changes() == null || patch.changes().isEmpty() || !FIELDS.containsAll(patch.changes().keySet())) {
            throw bad("Trường chỉnh sửa bản nháp không hợp lệ.");
        }
        var before = current(conversation, customer);
        requireVersion(before, patch);
        var data = data(before);
        if (patch.changes().containsKey("serviceId")
            && !Objects.equals(Objects.toString(data.get("serviceId"), null),
                Objects.toString(patch.changes().get("serviceId"), null))) {
            for (var dependent : List.of("packageId", "addOnIds", "answers", "promotionCode",
                "quantity", "requiredStaffCount")) data.remove(dependent);
            if (fromChat) {
                data.remove("startsAt");
                data.remove("addressId");
            }
        }
        patch.changes().forEach((key, value) -> {
            if (value == null) data.remove(key);
            else if ("answers".equals(key) && value instanceof Map<?, ?> updates) {
                var answers = new LinkedHashMap<>();
                if (data.get(key) instanceof Map<?, ?> previous) answers.putAll(previous);
                updates.forEach((field, answer) -> {
                    if (answer == null) answers.remove(field);
                    else answers.put(field, answer);
                });
                data.put(key, answers);
            } else data.put(key, value);
        });
        if (fromChat) {
            data.put("mode", "B");
            // Old chat drafts may contain a time that has since passed.
            if (!patch.changes().containsKey("startsAt") && data.containsKey("startsAt")
                && !future(data.get("startsAt"))) data.remove("startsAt");
        }
        normalize(data);
        var choices = choices(data, token);
        if (fromChat) applyChatDefaults(data, choices, patch, data(before).get("quantity"));
        validateChoices(data, choices);
        return new Prepared(patch, data, missing(data, choices), choices);
    }

    Map<String, Object> save(long conversation, long customer, Prepared prepared) {
        requireOwner(conversation, customer, true);
        if (!Boolean.TRUE.equals(conversations.sendExistsAiConversations(Boolean.class, conversation))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hội thoại đã kết thúc.");
        }
        var draft = current(conversation, customer);
        requireVersion(draft, prepared.patch());
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (draft == null) {
            draft = new BookingDraft();
            draft.setConversationId(conversation);
            draft.setCustomerId(customer);
            draft.setCreatedAt(now);
        }
        draft.setStatus("COLLECTING");
        draft.setUpdatedAt(now);
        draft.setExpiresAt(now.plus(Duration.ofHours(24)));
        draft.setDraftData(json.writeValueAsString(prepared.data()));
        draft.setMissingSlots(json.writeValueAsString(prepared.missing()));
        return response(drafts.saveAndFlush(draft), prepared.data(), prepared.missing(), prepared.choices());
    }

    String progress(Prepared prepared) {
        if (prepared.missing().contains("serviceId")) return "Bạn muốn đặt dịch vụ nào?";
        String serviceName = Objects.toString(prepared.choices().service().get("name"), "dịch vụ đã chọn");
        if (airConditionerCleaning(prepared.choices())) {
            if (prepared.missing().contains("quantity")) return "Bạn cần vệ sinh bao nhiêu máy lạnh?";
            if (prepared.missing().contains("requiredStaffCount")) {
                return "Bạn muốn 1 nhân viên làm lần lượt hay cần nhiều nhân viên để làm song song cho nhanh hơn? "
                    + "Nếu muốn làm song song, bạn cho mình biết cần mấy người. Bạn cũng có thể chọn ở bước đặt dịch vụ.";
            }
        }
        var answers = prepared.data().get("answers") instanceof Map<?, ?> values ? values : Map.of();
        var suggestions = prepared.choices().requirements().stream()
            .filter(field -> !hasAnswer(answers, field.get("fieldKey")))
            .sorted(Comparator.<Map<String, Object>, Boolean>comparing(field -> !Boolean.TRUE.equals(field.get("required")))
                .thenComparingInt(field -> field.get("displayOrder") instanceof Number order ? order.intValue() : 0))
            .limit(3).map(AiBookingDraftService::requirementHint).toList();
        String hint = suggestions.isEmpty() ? "" : "\n\nNếu biết, bạn có thể bổ sung trong chat:\n• "
            + String.join("\n• ", suggestions) + "\n\nHoặc ";
        return "Mình đã lưu đơn nháp " + serviceName + "." + hint
            + (suggestions.isEmpty() ? " Bấm" : "bấm")
            + " Xác nhận trên thẻ để chọn ngày giờ, địa chỉ và hoàn tất các thông tin còn thiếu ở bước đặt dịch vụ.";
    }

    private static boolean hasAnswer(Map<?, ?> answers, Object key) {
        Object value = answers.get(key);
        return value != null && !value.toString().isBlank()
            && !(value instanceof Collection<?> list && list.isEmpty());
    }

    private static String requirementHint(Map<String, Object> field) {
        String label = Objects.toString(field.get("label"), field.get("fieldKey").toString());
        if ("BOOLEAN".equals(field.get("fieldType"))) return label + " (Có / Không)";
        if (field.get("options") instanceof List<?> options && !options.isEmpty()) {
            return label + " (" + String.join(" / ", options.stream().limit(4).map(Object::toString).toList())
                + (options.size() > 4 ? " / …" : "") + ")";
        }
        return label;
    }

    private static boolean airConditionerCleaning(Choices choices) {
        return "AC_CLEANING".equals(choices.service().get("serviceType"));
    }

    private static void applyChatDefaults(Map<String, Object> data, Choices choices, Patch patch, Object previousQuantity) {
        if (choices.packages().size() == 1) data.putIfAbsent("packageId", choices.packages().getFirst().get("id"));
        if (!airConditionerCleaning(choices)) {
            if (choices.service().get("priceUnit") instanceof String priceUnit && !"ITEM".equals(priceUnit)) {
                data.putIfAbsent("quantity", 1L);
            }
            if (data.get("packageId") != null && data.get("requiredStaffCount") == null) {
                choices.packages().stream().filter(pack -> hasId(List.of(pack), data.get("packageId"))).findFirst()
                    .map(pack -> pack.get("defaultStaffCount")).ifPresent(staff -> data.put("requiredStaffCount", staff));
            }
            return;
        }
        if (!(data.get("quantity") instanceof Number units)) return;
        if (units.longValue() == 1) data.put("requiredStaffCount", 1L);
        else if (patch.changes().containsKey("quantity") && !units.toString().equals(Objects.toString(previousQuantity))
            && !patch.changes().containsKey("requiredStaffCount")) {
            data.remove("requiredStaffCount");
        }
        // The standard booking quote multiplies the chosen package by quantity.
        // Use the single-machine package when the customer has supplied a machine count.
        choices.packages().stream()
            .filter(pack -> pack.get("defaultStaffCount") instanceof Number staff && staff.intValue() == 1)
            .filter(pack -> pack.get("basePrice") instanceof Number)
            .min(Comparator.<Map<String, Object>>comparingDouble(pack -> ((Number) pack.get("basePrice")).doubleValue())
                .thenComparingLong(pack -> ((Number) pack.get("id")).longValue()))
            .ifPresent(pack -> data.put("packageId", pack.get("id")));
    }

    public record Patch(Long draftId, Long version, Map<String, Object> changes) {}

    private long actor(String token) {
        return ServiceHttpClient.userId(identity.authenticate(token, "CUSTOMER"));
    }

    private void requireOwner(long conversation, long customer, boolean lock) {
        if (conversations.ownedFindAiConversationsForUpdate(lock, conversation, customer).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại.");
        }
    }

    private BookingDraft current(long conversation, long customer) {
        return drafts.findFirstByConversationIdAndCustomerIdAndStatusAndExpiresAtAfterOrderByIdDesc(
            conversation, customer, "COLLECTING", Instant.now()).orElse(null);
    }

    private static void requireVersion(BookingDraft draft, Patch patch) {
        if (!Objects.equals(draft == null ? null : draft.getId(), patch.draftId())
            || !Objects.equals(draft == null ? null : draft.getVersion(), patch.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản nháp đã thay đổi. Hãy tải lại trước khi sửa.");
        }
    }

    private Map<String, Object> data(BookingDraft draft) {
        return draft == null ? new LinkedHashMap<>() : json.readValue(draft.getDraftData(), new TypeReference<>() {});
    }

    private record Choices(Map<String, Object> service, List<Map<String, Object>> addresses, List<Map<String, Object>> packages,
                           List<Map<String, Object>> addOns, List<Map<String, Object>> requirements) {}

    private Choices choices(Map<String, Object> data, String token) {
        var addresses = identity.list("/api/v1/identity/customer/addresses", token);
        if (data.get("serviceId") == null) return new Choices(Map.of(), addresses, List.of(), List.of(), List.of());
        var detail = catalog.get("/api/v1/catalog/services/" + data.get("serviceId"), null);
        return new Choices(service(detail), addresses, items(detail, "packages"), items(detail, "addOns"), items(detail, "requirements"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(Map<String, Object> detail) {
        return (Map<String, Object>) detail.getOrDefault("service", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> data, String key) {
        return (List<Map<String, Object>>) data.getOrDefault(key, List.of());
    }

    private static Map<String, Object> response(BookingDraft draft, Map<String, Object> data,
                                                List<String> missing, Choices choices) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", draft == null ? null : draft.getId());
        result.put("version", draft == null ? null : draft.getVersion());
        result.put("expiresAt", draft == null ? null : draft.getExpiresAt());
        result.put("data", data);
        result.put("missingSlots", missing);
        result.put("choices", Map.of("service", choices.service(), "addresses", choices.addresses(),
            "packages", choices.packages(), "addOns", choices.addOns(), "requirements", choices.requirements()));
        return result;
    }

    private static void normalize(Map<String, Object> data) {
        for (String key : List.of("serviceId", "packageId", "addressId", "quantity", "requiredStaffCount")) {
            if (data.containsKey(key)) data.put(key, positive(data.get(key), key));
        }
        if (data.containsKey("mode") && !List.of("A", "B").contains(data.get("mode"))) throw bad("Chế độ đặt lịch không hợp lệ.");
        if (data.containsKey("startsAt")) {
            try {
                var local = LocalDateTime.parse(Objects.toString(data.get("startsAt")));
                data.put("startsAt", local.toString());
            } catch (DateTimeException ex) {
                throw bad("Hãy nhập ngày và giờ cụ thể theo múi giờ Việt Nam.");
            }
        }
        if (data.containsKey("addOnIds")) {
            if (!(data.get("addOnIds") instanceof List<?> ids) || ids.size() > 20) throw bad("Danh sách tiện ích không hợp lệ.");
            data.put("addOnIds", ids.stream().map(id -> positive(id, "addOnIds")).distinct().toList());
        }
        if (data.containsKey("promotionCode") && (!(data.get("promotionCode") instanceof String code)
            || code.isBlank() || code.length() > 60)) throw bad("Mã ưu đãi không hợp lệ.");
        if (data.containsKey("answers") && (!(data.get("answers") instanceof Map<?, ?> answers)
            || answers.size() > 50 || answers.toString().length() > 12000)) throw bad("Câu trả lời dịch vụ không hợp lệ.");
    }

    private static long positive(Object raw, String key) {
        if (raw instanceof Integer || raw instanceof Long) {
            long value = ((Number) raw).longValue();
            if (value > 0) return value;
        }
        throw bad("Giá trị không hợp lệ: " + key);
    }

    private static boolean hasId(List<Map<String, Object>> choices, Object id) {
        return choices.stream().anyMatch(item -> item.get("id") instanceof Number n
            && id instanceof Number selected && n.longValue() == selected.longValue());
    }

    private static void validateChoices(Map<String, Object> data, Choices choices) {
        if (data.containsKey("addressId") && !hasId(choices.addresses(), data.get("addressId"))) throw bad("Địa chỉ không thuộc tài khoản.");
        if (data.containsKey("packageId") && !hasId(choices.packages(), data.get("packageId"))) throw bad("Gói không thuộc dịch vụ.");
        if (data.get("addOnIds") instanceof List<?> ids && ids.stream().anyMatch(id -> !hasId(choices.addOns(), id))) {
            throw bad("Tiện ích không thuộc dịch vụ.");
        }
        if (data.get("answers") instanceof Map<?, ?> answers && answers.keySet().stream().anyMatch(key ->
            choices.requirements().stream().noneMatch(field -> Objects.equals(field.get("fieldKey"), key)))) {
            throw bad("Câu trả lời chứa trường không thuộc dịch vụ.");
        }
        if (data.containsKey("startsAt") && !future(data.get("startsAt"))) throw bad("Giờ bắt đầu phải ở tương lai.");
    }

    private static boolean future(Object raw) {
        return LocalDateTime.parse(raw.toString()).isAfter(LocalDateTime.now(CUSTOMER_ZONE));
    }

    private static List<String> missing(Map<String, Object> data, Choices choices) {
        var missing = new ArrayList<>(REQUIRED.stream().filter(key -> !data.containsKey(key)).toList());
        if (data.containsKey("startsAt") && !future(data.get("startsAt"))) missing.add("startsAt");
        if (data.containsKey("addressId") && !hasId(choices.addresses(), data.get("addressId"))) missing.add("addressId");
        if (data.containsKey("packageId") && !hasId(choices.packages(), data.get("packageId"))) missing.add("packageId");
        var answers = data.get("answers") instanceof Map<?, ?> values ? values : Map.of();
        for (var field : choices.requirements()) {
            if (!Boolean.TRUE.equals(field.get("required"))) continue;
            var key = field.get("fieldKey");
            if (!hasAnswer(answers, key)) {
                missing.add("answers." + key);
            }
        }
        return missing;
    }

    private static ResponseStatusException bad(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
