package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.domesticservice.booking.service.BookingOperationsService.*;
import com.domesticservice.booking.repository.BookingChatSqlRepository;

@Service
public class BookingChatService {

    private final BookingOperationsService ops;

    private final BookingChatSqlRepository repository;

    private final tools.jackson.databind.ObjectMapper json;

    public BookingChatService(BookingOperationsService ops, BookingChatSqlRepository repository, tools.jackson.databind.ObjectMapper json) {
        this.ops = ops;
        this.repository = repository;
        this.json = json;
    }

    private Map<String, Object> conversation(long user, String role, long id, boolean lock) {
        var rows = repository.conversationFindConversationsForUpdate(role, lock, id, user);
        if (rows.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại của tài khoản này.");
        return rows.getFirst();
    }

    public List<Map<String, Object>> list(String token) {
        var user = ops.actor(token, "CUSTOMER", "STAFF");
        if ("CUSTOMER".equals(user.get("role"))) repository.archiveExpired();
        var people = new HashMap<Long, Map<String, Object>>();
        for (var person : ops.identityClient().list("/api/v1/identity/staff-directory", token)) people.put(ServiceHttpClient.userId(person), person);
        return repository.listCountConversations(Objects.toString(user.get("role")), user.get("id")).stream().map(c -> {
            var service = ops.object(c.get("service_snapshot"));
            var address = ops.object(c.get("address_snapshot"));
            var staff = people.getOrDefault(((Number) c.get("staff_id")).longValue(), Map.of());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.get("id"));
            item.put("bookingId", c.get("booking_id"));
            item.put("bookingCode", c.get("booking_code"));
            item.put("customerId", c.get("customer_id"));
            item.put("staffId", c.get("staff_id"));
            item.put("customerName", Objects.toString(address.get("recipientName"), Objects.toString(address.get("receiverName"), "Khách hàng #" + c.get("customer_id"))));
            item.put("customerAvatar", "");
            item.put("staffName", Objects.toString(staff.get("fullName"), "Nhân viên #" + c.get("staff_id")));
            item.put("staffAvatar", Objects.toString(staff.get("avatar"), ""));
            item.put("serviceName", Objects.toString(service.get("name"), Objects.toString(service.get("serviceName"), "")));
            item.put("bookingStatus", c.get("booking_status"));
            item.put("conversationStatus", c.get("status"));
            item.put("createdAt", instant(c.get("created_at")));
            item.put("customerDeletedThroughId", "CUSTOMER".equals(user.get("role")) ? c.get("customer_deleted_through_id") : 0);
            item.put("customerTrashedAt", c.get("customer_trashed_at") == null ? null : instant(c.get("customer_trashed_at")));
            item.put("lastMessage", Objects.toString(c.get("last_content"), ""));
            item.put("lastMessageTime", instant(c.get("last_sent_at") == null ? c.get("created_at") : c.get("last_sent_at")));
            item.put("unreadCountCustomer", c.get("customer_unread"));
            item.put("unreadCountStaff", c.get("staff_unread"));
            return item;
        }).toList();
    }

    public List<Map<String, Object>> messages(String token, long id) {
        var user = ops.actor(token, "CUSTOMER", "STAFF");
        var c = conversation(ServiceHttpClient.userId(user), (String) user.get("role"), id, false);
        long deletedThrough = "CUSTOMER".equals(user.get("role")) ? ((Number)c.get("customer_deleted_through_id")).longValue() : 0;
        return repository.messagesFindMessages(id, deletedThrough);
    }

    @Transactional
    public Map<String, Object> send(String token, long id, Map<String, Object> b) {
        var user = ops.actor(token, "CUSTOMER", "STAFF");
        var c = conversation(ServiceHttpClient.userId(user), (String) user.get("role"), id, true);
        String key = text(b, "clientKey", 160, true), content = text(b, "content", 4000, true);
        var previous = repository.sendFindMessages(id, user.get("id"), key);
        if (!previous.isEmpty()) {
            if (!content.equals(previous.getFirst().get("content")))
                throw error(HttpStatus.CONFLICT, "Mã tin nhắn đã được dùng với nội dung khác.");
            return Map.of("id", previous.getFirst().get("id"));
        }
        if (!"ACTIVE".equals(c.get("status")) || List.of("COMPLETED", "CANCELLED", "REFUNDED", "REFUNDING", "ABSENT", "REJECTED").contains(c.get("booking_status")))
            throw error(HttpStatus.CONFLICT, "Ca đã kết thúc; hội thoại chỉ được đọc.");
        repository.reopenForCustomer(id);
        long message = repository.sendInsertMessages(id, user.get("id"), user.get("role"), content, key);
        long recipient = ((Number) c.get("customer_id")).longValue() == ServiceHttpClient.userId(user) ? ((Number) c.get("staff_id")).longValue() : ((Number) c.get("customer_id")).longValue();
        var payload = Map.of("messageId", message, "conversationId", id, "recipientId", recipient, "senderId", user.get("id"), "senderName", Objects.toString(user.get("fullName"), "Người gửi"));
        repository.sendInsertOutboxEvents(c.get("booking_id"), json.writeValueAsString(payload));
        return Map.of("id", message);
    }

    @Transactional
    public Map<String, Object> read(String token, long id, Map<String, Object> body) {
        var user = ops.actor(token, "CUSTOMER", "STAFF");
        var c=conversation(ServiceHttpClient.userId(user), (String) user.get("role"), id, false);
        long deletedThrough="CUSTOMER".equals(user.get("role")) ? ((Number)c.get("customer_deleted_through_id")).longValue() : 0;
        long through = body == null || !body.containsKey("throughMessageId") ? Long.MAX_VALUE : num(body, "throughMessageId", 0, 0, Long.MAX_VALUE);
        int changed = repository.readUpdateMessages(id, user.get("id"), through, deletedThrough);
        long unread = repository.readCountMessages(Long.class, id, "CUSTOMER".equals(user.get("role")) ? "STAFF" : "CUSTOMER", deletedThrough);
        return Map.of("changed", changed, "unreadCount", unread);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000, initialDelay=30000)
    public void archiveExpired() {
        repository.archiveExpired();
    }

    @Transactional
    public void deletePermanently(String token, long id) {
        var user=ops.actor(token,"CUSTOMER");
        // Lock the conversation first: sending a new message uses the same lock.
        conversation(ServiceHttpClient.userId(user),"CUSTOMER",id,true);
        repository.archiveExpired(id);
        var c=conversation(ServiceHttpClient.userId(user),"CUSTOMER",id,false);
        if (c.get("customer_trashed_at")==null)
            throw error(HttpStatus.CONFLICT,"Chỉ được xóa vĩnh viễn hội thoại đang ở trong thùng rác.");
        repository.deleteCustomerHistory(id);
    }

}
