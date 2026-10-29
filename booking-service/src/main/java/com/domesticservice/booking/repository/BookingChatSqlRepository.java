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
        String visible = role.equals("CUSTOMER") ? " AND m.id>c.customer_deleted_through_id" : "";
        String query = """
            SELECT c.*,b.status AS booking_status,b.booking_code,b.service_snapshot,b.address_snapshot,
                   recent.content AS last_content,recent.sent_at AS last_sent_at,
                   counts.customer_unread,counts.staff_unread
            FROM conversations c JOIN bookings b ON b.id=c.booking_id
            LEFT JOIN LATERAL (SELECT m.content,m.sent_at FROM messages m WHERE m.conversation_id=c.id
            """ + visible + " ORDER BY m.sent_at DESC,m.id DESC LIMIT 1) recent ON true " + """
            LEFT JOIN LATERAL (SELECT count(*) FILTER (WHERE m.sender_type='STAFF') AS customer_unread,
                count(*) FILTER (WHERE m.sender_type='CUSTOMER') AS staff_unread
                FROM messages m WHERE m.conversation_id=c.id AND m.read_at IS NULL
            """ + visible + ") counts ON true WHERE " + own + "=?"
            + (role.equals("CUSTOMER") ? " AND (c.customer_deleted_at IS NULL OR recent.sent_at IS NOT NULL)" : "")
            + " ORDER BY coalesce(recent.sent_at,c.created_at) DESC,c.id DESC";
        return db.queryForList(query, args);
    }

    public List<Map<String, Object>> messagesFindMessages(long id, long deletedThrough) {
        return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_id AS \"senderId\",sender_type AS \"senderType\",message_type AS \"contentType\",content,media_url AS \"mediaUrl\",sent_at AS \"sentAt\",(read_at IS NOT NULL) AS \"isRead\" FROM messages WHERE conversation_id=? AND id>? ORDER BY sent_at,id", id, deletedThrough);
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
        return db.update("UPDATE messages SET read_at=now() WHERE conversation_id=? AND sender_id IS DISTINCT FROM ? AND read_at IS NULL AND id<=? AND id>?", args);
    }

    public <T> T readCountMessages(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=? AND sender_type=? AND read_at IS NULL AND id>?", type, args);
    }
    /** Called both periodically and before reads/deletion so the 24-hour boundary is exact. */
    @org.springframework.transaction.annotation.Transactional
    public int archiveExpired() { return archiveExpired(null); }

    @org.springframework.transaction.annotation.Transactional
    public int archiveExpired(Long conversationId) {
        String query = """
            UPDATE conversations c SET customer_trashed_at=coalesce(
                (SELECT max(m.sent_at) FROM messages m WHERE m.conversation_id=c.id AND m.id>c.customer_deleted_through_id),c.created_at)+interval '24 hours'
            WHERE c.customer_trashed_at IS NULL
              AND (c.customer_deleted_at IS NULL OR EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id=c.id AND m.id>c.customer_deleted_through_id))
              AND coalesce((SELECT max(m.sent_at) FROM messages m WHERE m.conversation_id=c.id AND m.id>c.customer_deleted_through_id),c.created_at)<=now()-interval '24 hours'
            """;
        return conversationId == null ? db.update(query) : db.update(query + " AND c.id=?",conversationId);
    }

    public void reopenForCustomer(long id) {
        db.update("UPDATE conversations SET customer_trashed_at=NULL WHERE id=?",id);
    }

    public void deleteCustomerHistory(long id) {
        // A high-water mark permanently removes this customer's copy; staff history is independent.
        db.update("""
            UPDATE conversations SET customer_deleted_through_id=greatest(customer_deleted_through_id,
                coalesce((SELECT max(id) FROM messages WHERE conversation_id=?),0)),customer_trashed_at=NULL,customer_deleted_at=now()
            WHERE id=?
            """,id,id);

    }

}
