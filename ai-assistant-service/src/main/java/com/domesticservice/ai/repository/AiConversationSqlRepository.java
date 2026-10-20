package com.domesticservice.ai.repository;

import java.util.*;
import com.domesticservice.ai.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class AiConversationSqlRepository {

    private final NativeQueryExecutor db;

    public AiConversationSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> ownedFindAiConversationsForUpdate(boolean lock, Object... args) {
        return db.queryForList("SELECT id FROM ai_conversations WHERE id=? AND customer_id=?" + (lock ? " FOR UPDATE" : ""), args);
    }

    public List<Map<String, Object>> conversationsFindAiConversations(Object... args) {
        return db.queryForList("SELECT id,title,status,started_at AS \"startedAt\" FROM ai_conversations WHERE customer_id=? AND status='ACTIVE' ORDER BY id DESC", args);
    }

    public <T> T createFindAiConversations(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_conversations(customer_id,title) VALUES (?,'Trợ lý dịch vụ') RETURNING id", type, args);
    }

    public List<Map<String, Object>> messagesFindAiMessages(Object... args) {
        return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_type AS role,content,created_at AS \"createdAt\" FROM ai_messages WHERE conversation_id=? ORDER BY id", args);
    }

    public List<Map<String, Object>> sendFindAiMessages(Object... args) {
        return db.queryForList("SELECT id,content FROM ai_messages WHERE conversation_id=? AND client_key=?", args);
    }

    public <T> T sendExistsAiConversations(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_conversations WHERE id=? AND status='ACTIVE')", type, args);
    }

    public List<Map<String, Object>> sendFindAiMessages2(Object... args) {
        return db.queryForList("SELECT sender_type AS role,content FROM ai_messages WHERE conversation_id=? ORDER BY id DESC LIMIT 20", args);
    }

    public <T> T sendFindAiMessages3(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content,client_key) VALUES (?,'USER',?,?) RETURNING id", type, args);
    }

    public <T> T sendFindAiMessages4(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content) VALUES (?,'ASSISTANT',?) RETURNING id", type, args);
    }

    public int clearUpdateAiConversations(Object... args) {
        return db.update("UPDATE ai_conversations SET status='ENDED',ended_at=now() WHERE id=?", args);
    }
}
