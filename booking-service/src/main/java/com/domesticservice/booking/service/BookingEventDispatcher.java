package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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

    public BookingEventDispatcher(BookingEventDispatcherSqlRepository repository, ObjectMapper json, BookingWorkflowService flow, PlatformTransactionManager manager, @Value("${clients.identity.base-url}") String url, @Value("${INTERNAL_SERVICE_TOKEN:}") String key) {
        this.repository = repository;
        this.json = json;
        this.flow = flow;
        identity = new ServiceHttpClient(url);
        this.key = key;
        tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        if (key.isBlank())
            return;
        try {
            var rows = repository.dispatchFindOutboxEvents(Long.class);
            for (long id : rows) tx.executeWithoutResult(status -> {
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
                    users.addAll(repository.dispatchFindBookingAssignments(Long.class, b.get("id")));
                    boolean created = "PENDING".equals(payload.get("oldStatus")) && List.of("MATCHING", "STAFF_ASSIGNED").contains(state);
                    if ("MATCHING".equals(state) && "MODE_B".equals(b.get("booking_mode"))) {
                        for (var staff : identity.list("/api/v1/identity/staff-directory", null)) {
                            long staffId = ServiceHttpClient.userId(staff);
                            var prefs = repository.dispatchFindStaffScheduleSettings(Boolean.class, staffId);
                            if ((prefs.isEmpty() || prefs.getFirst()) && flow.eligibility(staffId, b, staff).isEmpty())
                                users.add(staffId);
                        }
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
                    repository.dispatchUpdateOutboxEvents3(id);
                } catch (RuntimeException ex) {
                    repository.dispatchUpdateOutboxEvents4(id);
                }
            });
        } catch (RuntimeException ignored) {
            /* Database/identity outage is retried; no fabricated notification is emitted. */
        }
    }
}
