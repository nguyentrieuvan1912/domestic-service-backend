package com.domesticservice.identity.repository;

import java.util.*;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityOperationsSqlRepository {

    private final NativeQueryExecutor db;

    public IdentityOperationsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> eventFindUsers(Object... args) {
        return db.queryForList("SELECT id,role FROM users WHERE id=?", args);
    }

    public int eventInsertNotifications(Object... args) {
        return db.update("INSERT INTO notifications(user_id,notification_type,title,content,reference_id,payload,event_key) VALUES (?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT(user_id,event_key) WHERE event_key IS NOT NULL DO NOTHING", args);
    }

    public int eventUpdateCustomers(Object... args) {
        return db.update("UPDATE customers SET total_bookings=total_bookings+1 WHERE id=?", args);
    }

    public List<Map<String, Object>> supportFindSupportRequests(Object... args) {
        return db.queryForList("SELECT id,sender_name AS name,email,subject,message,status,admin_note AS \"adminNote\",created_at AS \"createdAt\" FROM support_requests ORDER BY id DESC LIMIT 500", args);
    }

    public int supportUpdateSupportRequests(Object... args) {
        return db.update("UPDATE support_requests SET status=?,admin_note=?,updated_at=now() WHERE id=?", args);
    }
}
