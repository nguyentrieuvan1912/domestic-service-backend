package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.domesticservice.booking.repository.BookingEventDispatcherSqlRepository;

@Component
@EnableScheduling
public class BookingEventDispatcher {

    private final BookingEventDispatcherSqlRepository repository;

    private final ObjectMapper json;

    private final ServiceHttpClient identity;

    private final String key;

    private final TransactionTemplate tx;

    private final BookingWorkflowService flow;
    private final StaffDispatchService staffDispatch;

    public BookingEventDispatcher(BookingEventDispatcherSqlRepository repository, ObjectMapper json, BookingWorkflowService flow,
                                  StaffDispatchService staffDispatch, PlatformTransactionManager manager,
                                  @Value("${clients.identity.base-url}") String url, @Value("${INTERNAL_SERVICE_TOKEN:}") String key) {
        this.repository = repository;
        this.json = json;
        this.flow = flow;
        this.staffDispatch = staffDispatch;
        identity = new ServiceHttpClient(url);
        this.key = key;
        tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        if (key.isBlank())
            return;
        try {
            var rows = repository.dispatchFindOutboxEvents();
            JsonNode plannedTeams = rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("plan_needed")))
                ? planTeams() : json.createObjectNode();
            for (var row : rows) tx.executeWithoutResult(status -> {
                long id = ((Number) row.get("id")).longValue();
                var events = repository.dispatchFindOutboxEventsForUpdate(id);
                if (events.isEmpty())
                    return;
                var event = events.getFirst();
                try {
                    var books = repository.dispatchFindBookings(event.get("aggregate_id"));
                    if (books.isEmpty()) {
                        repository.dispatchUpdateOutboxEvents(id);
                        return;
                    }
                    var b = books.getFirst();
                    var payload = json.readValue(event.get("payload").toString(), new TypeReference<Map<String, Object>>() {
                    });
                    if ("MATCHING_BROADCAST".equals(event.get("event_type"))) {
                        broadcastRemaining(id, b, json.valueToTree(payload).path("notified"));
                        repository.dispatchUpdateOutboxEvents2(id);
                        return;
                    }
                    if ("CALL_INCOMING".equals(event.get("event_type"))) {
                        identity.internalEvent(key,Map.of("eventKey","call:"+payload.get("callId"),"userIds",List.of(payload.get("recipientId")),"type","CALL_INCOMING","title","Cuộc gọi từ "+payload.get("callerName"),"content","Cuộc gọi về đơn "+b.get("booking_code")+". Nhấn để trả lời.","referenceId",payload.get("callId"),"data",payload,"bookingCreated",false));
                        repository.dispatchUpdateOutboxEvents2(id);return;
                    }
                    if ("CHAT_MESSAGE".equals(event.get("event_type"))) {
                        identity.internalEvent(key, Map.of("eventKey", "chat-message:" + payload.get("messageId"), "userIds", List.of(payload.get("recipientId")), "type", "CHAT_MESSAGE", "title", "Tin nhắn mới từ " + payload.get("senderName"), "content", "Bạn có tin nhắn mới về đơn " + b.get("booking_code") + ". Nhấn để mở cuộc trò chuyện.", "referenceId", payload.get("conversationId"), "data", Map.of("bookingId", b.get("id"), "conversationId", payload.get("conversationId"), "messageId", payload.get("messageId")), "bookingCreated", false));
                        repository.dispatchUpdateOutboxEvents2(id);
                        return;
                    }
                    String state = Objects.toString(payload.get("status"), Objects.toString(event.get("event_type"))), code = Objects.toString(b.get("booking_code"));
                    Set<Long> users = new LinkedHashSet<>();
                    users.add(((Number) b.get("customer_id")).longValue());
                    if (!"AWAITING_PAYMENT".equals(state)) users.addAll(repository.dispatchFindBookingAssignments(Long.class, b.get("id")));
                    boolean created = ("PENDING".equals(payload.get("oldStatus")) || "AWAITING_PAYMENT".equals(payload.get("oldStatus"))) && List.of("MATCHING", "STAFF_ASSIGNED").contains(state);
                    List<Long> preferred = List.of();
                    boolean widenLater = false;
                    if ("MATCHING".equals(state) && openMatching(b)) {
                        var eligible = eligibleMatchingStaff(b, users);
                        int remaining = ((Number) b.get("remaining")).intValue();
                        preferred = plannedTeams.path(b.get("id").toString()).valueStream()
                            .map(candidate -> candidate.path("staffId").asLong()).distinct().toList();
                        boolean fullPlan = preferred.size() == remaining && eligible.containsAll(preferred)
                            && ((Timestamp) b.get("starts_at")).toInstant().isAfter(Instant.now().plusSeconds(60));
                        users.addAll(fullPlan ? preferred : eligible);
                        widenLater = fullPlan;
                    }
                    String type = created ? "BOOKING_CREATED" : state.equals("COMPLETED") ? "SERVICE_COMPLETED" : state.equals("CANCELLED") ? "BOOKING_CANCELLED" : state.equals("IN_PROGRESS") ? "SERVICE_IN_PROGRESS" : state.equals("CONFIRMED") ? "STAFF_ACCEPTED" : "SYSTEM";
                    boolean expired = StaffResponseService.TIMEOUT_REASON.equals(payload.get("reason"));
                    boolean renewed = "STAFF_ASSIGNED".equals(state) && "CANCELLED".equals(payload.get("oldStatus"));
                    if (expired) {
                        long customer = ((Number) b.get("customer_id")).longValue();
                        identity.internalEvent(key, Map.of("eventKey", "booking:" + id + ":customer", "userIds", List.of(customer), "type", "BOOKING_CANCELLED", "title", "Nhân viên chưa nhận đơn " + code, "content", "Nhân viên bạn chọn đang bận. Bạn có muốn chờ thêm 5 phút hoặc tìm nhân viên khác?", "referenceId", b.get("id"), "data", Map.of("bookingId", b.get("id")), "bookingCreated", false));
                        users.remove(customer);
                        if (!users.isEmpty())
                            identity.internalEvent(key, Map.of("eventKey", "booking:" + id + ":staff", "userIds", users.stream().toList(), "type", "BOOKING_CANCELLED", "title", "Hết thời gian nhận đơn " + code, "content", "Lời mời đã hủy do hết thời gian nhận đơn. Hãy chú ý lời mời và phản hồi sớm hơn để có doanh thu tốt hơn.", "referenceId", b.get("id"), "data", Map.of("bookingId", b.get("id")), "bookingCreated", false));
                    } else
                        identity.internalEvent(key, Map.of("eventKey", "booking:" + id, "userIds", users.stream().toList(), "type", renewed ? "BOOKING_CREATED" : type, "title", "Đơn " + code, "content", renewed ? "Khách hàng chờ thêm 5 phút. Nhân viên vui lòng phản hồi lời mời nhận đơn." : "Trạng thái đơn " + code + ": " + state + ".", "referenceId", b.get("id"), "data", Map.of("bookingId", b.get("id")), "bookingCreated", created || renewed));
                    if (widenLater) repository.queueMatchingBroadcast(((Number) b.get("id")).longValue(),
                        json.writeValueAsString(Map.of("notified", preferred)));
                    repository.dispatchUpdateOutboxEvents3(id);
                } catch (RuntimeException ex) {
                    repository.dispatchUpdateOutboxEvents4(id);
                }
            });
        } catch (RuntimeException ignored) {
            /* Database/identity outage is retried; no fabricated notification is emitted. */
        }
    }

    private JsonNode planTeams() {
        try {
            var run = staffDispatch.runAutomatically();
            if (run != null) return run.result().path("solver").path("proposal").path("teams");
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Automatic staff dispatch could not calculate a plan", failure);
        }
        return json.createObjectNode();
    }

    private Set<Long> eligibleMatchingStaff(Map<String, Object> booking, Set<Long> excluded) {
        Set<Long> eligible = new LinkedHashSet<>();
        for (var staff : identity.list("/api/v1/identity/staff-directory", null)) {
            long staffId = ServiceHttpClient.userId(staff);
            if (excluded.contains(staffId)) continue;
            var prefs = repository.dispatchFindStaffScheduleSettings(Boolean.class, staffId);
            if ((prefs.isEmpty() || prefs.getFirst()) && flow.eligibility(staffId, booking, staff).isEmpty()) eligible.add(staffId);
        }
        return eligible;
    }

    private static boolean openMatching(Map<String, Object> booking) {
        return "MODE_B".equals(booking.get("booking_mode")) && "MATCHING".equals(booking.get("status"))
            && Boolean.TRUE.equals(booking.get("pricing_confirmed")) && ((Number) booking.get("remaining")).intValue() > 0
            && ("CASH".equals(booking.get("payment_method")) || "PAID".equals(booking.get("payment_status")))
            && ((Timestamp) booking.get("starts_at")).toInstant().isAfter(Instant.now());
    }

    private void broadcastRemaining(long id, Map<String, Object> booking, JsonNode notified) {
        if (!openMatching(booking)) return;
        var excluded = new HashSet<>(repository.dispatchFindBookingAssignments(Long.class, booking.get("id")));
        notified.valueStream().forEach(staff -> excluded.add(staff.asLong()));
        var recipients = eligibleMatchingStaff(booking, excluded);
        if (recipients.isEmpty()) return;
        identity.internalEvent(key, Map.of("eventKey", "booking:" + id, "userIds", recipients.stream().toList(),
            "type", "BOOKING_CREATED", "title", "Ca đang cần nhân viên",
            "content", "Đơn " + booking.get("booking_code") + " còn vị trí trống. Mở ứng dụng để xem ca.",
            "referenceId", booking.get("id"), "data", Map.of("bookingId", booking.get("id")), "bookingCreated", false));
    }
}
