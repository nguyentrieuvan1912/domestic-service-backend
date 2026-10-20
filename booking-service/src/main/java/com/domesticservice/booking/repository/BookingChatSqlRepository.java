package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingChatSqlRepository {

    private final NativeQueryExecutor db;

    public BookingChatSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> conversationFindConversationsForUpdate(String role, boolean lock, Object... args) {
        String own = role.equals("STAFF") ? "c.staff_id" : "c.customer_id";
        return db.queryForList("SELECT c.*,b.status AS booking_status,b.booking_code,b.service_snapshot,b.address_snapshot FROM conversations c JOIN bookings b ON b.id=c.booking_id WHERE c.id=? AND " + own + "=?" + (lock ? " FOR UPDATE OF c" : ""), args);
    }

    public List<Map<String, Object>> listCountConversations(String role, Object... args) {
        String own = role.equals("STAFF") ? "c.staff_id" : "c.customer_id";
        String query = """
            SELECT c.*,b.status AS booking_status,b.booking_code,b.service_snapshot,b.address_snapshot,
                   recent.content AS last_content,recent.sent_at AS last_sent_at,
                   counts.customer_unread,counts.staff_unread
            FROM conversations c JOIN bookings b ON b.id=c.booking_id
            LEFT JOIN LATERAL (SELECT content,sent_at FROM messages WHERE conversation_id=c.id ORDER BY sent_at DESC,id DESC LIMIT 1) recent ON true
            LEFT JOIN LATERAL (SELECT count(*) FILTER (WHERE sender_type='STAFF') AS customer_unread,
                count(*) FILTER (WHERE sender_type='CUSTOMER') AS staff_unread
                FROM messages WHERE conversation_id=c.id AND read_at IS NULL) counts ON true
            WHERE""" + " " + own + "=? ORDER BY c.created_at DESC";
        return db.queryForList(query, args);
    }

    public List<Map<String, Object>> messagesFindMessages(Object... args) {
        return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_id AS \"senderId\",sender_type AS \"senderType\",message_type AS \"contentType\",content,media_url AS \"mediaUrl\",sent_at AS \"sentAt\",(read_at IS NOT NULL) AS \"isRead\" FROM messages WHERE conversation_id=? ORDER BY sent_at,id", args);
    }

    public List<Map<String, Object>> sendFindMessages(Object... args) {
        return db.queryForList("SELECT id,content FROM messages WHERE conversation_id=? AND sender_id=? AND client_key=?", args);
    }

    public long sendInsertMessages(Object... args) {
        return insert("INSERT INTO messages(conversation_id,sender_id,sender_type,message_type,content,client_key) VALUES (?,?,?,'TEXT',?,?)", args);
    }

    public long sendInsertOutboxEvents(Object... args) {
        return insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'CHAT_MESSAGE',CAST(? AS jsonb))", args);
    }

    public int readUpdateMessages(Object... args) {
        return db.update("UPDATE messages SET read_at=now() WHERE conversation_id=? AND sender_id IS DISTINCT FROM ? AND read_at IS NULL AND id<=?", args);
    }

    public <T> T readCountMessages(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=? AND sender_type=? AND read_at IS NULL", type, args);
    }
}
