package com.domesticservice.ai.repository;

import java.util.*;
import com.domesticservice.ai.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

@Repository
public class AiConversationSqlRepository {

    private final NativeQueryExecutor db;
    private final ObjectMapper json;

    public AiConversationSqlRepository(NativeQueryExecutor db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> ownedFindAiConversationsForUpdate(boolean lock, Object... args) {
        return db.queryForList("SELECT id FROM ai_conversations WHERE id=? AND customer_id=?" + (lock ? " FOR UPDATE" : ""), args);
    }

    public Long expandedDraftId(long conversation) {
        return db.queryForObject("SELECT expanded_draft_id FROM ai_conversations WHERE id=?", Long.class, conversation);
    }

    public void setExpandedDraft(long conversation, Long draftId) {
        db.update("UPDATE ai_conversations SET expanded_draft_id=? WHERE id=?", draftId, conversation);
    }

    public List<Map<String, Object>> conversationsFindAiConversations(Object... args) {
        return db.queryForList("SELECT id,title,status,started_at AS \"startedAt\" FROM ai_conversations WHERE customer_id=? AND status='ACTIVE' ORDER BY id DESC", args);
    }

    public <T> T createFindAiConversations(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_conversations(customer_id,title) VALUES (?,'Trợ lý dịch vụ') RETURNING id", type, args);
    }

    public List<Map<String, Object>> messagesFindAiMessages(Object... args) {
        return decodeSuggestions(db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_type AS role,content,suggestions::text AS suggestions,created_at AS \"createdAt\" FROM ai_messages WHERE conversation_id=? ORDER BY id", args));
    }

    public List<Map<String, Object>> turnMessages(long conversation, String key, long reply) {
        return decodeSuggestions(db.queryForList("""
            SELECT id,conversation_id AS "conversationId",sender_type AS role,content,suggestions::text AS suggestions,created_at AS "createdAt"
            FROM ai_messages WHERE conversation_id=? AND (client_key=? OR id=?) ORDER BY id
            """, conversation, key, reply));
    }

    private List<Map<String, Object>> decodeSuggestions(List<Map<String, Object>> messages) {
        for (var message : messages) {
            message.put("suggestions", json.readValue(message.get("suggestions").toString(),
                new TypeReference<List<Map<String, Object>>>() {}));
        }
        return messages;
    }

    public void saveSuggestions(long message, List<Map<String, Object>> suggestions) {
        db.update("UPDATE ai_messages SET suggestions=CAST(? AS jsonb) WHERE id=?", json.writeValueAsString(suggestions), message);
    }

    public List<Map<String, Object>> sendFindAiMessages(Object... args) {
        return db.queryForList("SELECT id,content FROM ai_messages WHERE conversation_id=? AND client_key=?", args);
    }

    public <T> T sendExistsAiConversations(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_conversations WHERE id=? AND status='ACTIVE')", type, args);
    }

    public List<Map<String, Object>> sendFindAiMessages2(Object... args) {
        return db.queryForList("SELECT sender_type AS role,content FROM ai_messages WHERE conversation_id=? AND sender_type IN ('USER','ASSISTANT') ORDER BY id DESC LIMIT 20", args);
    }

    public <T> T sendFindAiMessages3(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content,client_key) VALUES (?,'USER',?,?) RETURNING id", type, args);
    }

    public <T> T sendFindAiMessages4(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content) VALUES (?,'ASSISTANT',?) RETURNING id", type, args);
    }

    public List<Map<String, Object>> findTurn(long conversation, String key) {
        return db.queryForList("SELECT request_hash,response_id FROM ai_chat_turns WHERE conversation_id=? AND client_key=?",
            conversation, key);
    }

    public boolean hasRunningTurn(long conversation) {
        return Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ai_chat_turns WHERE conversation_id=? AND response_id IS NULL AND lease_until>now())",
            Boolean.class, conversation));
    }

    public void startTurn(long conversation, String key, String hash, String attempt) {
        db.update("INSERT INTO ai_chat_turns(conversation_id,client_key,request_hash,attempt_key,lease_until)"
            + " VALUES (?,?,?,?,now()+interval '240 seconds')"
            + " ON CONFLICT (conversation_id,client_key) DO UPDATE SET attempt_key=EXCLUDED.attempt_key,lease_until=EXCLUDED.lease_until",
            conversation, key, hash, attempt);
    }

    public boolean ownsTurn(long conversation, String key, String attempt) {
        return Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ai_chat_turns WHERE conversation_id=? AND client_key=? AND attempt_key=? AND response_id IS NULL AND lease_until>now())",
            Boolean.class, conversation, key, attempt));
    }

    public void completeTurn(long conversation, String key, String attempt, long reply) {
        db.update("UPDATE ai_chat_turns SET response_id=? WHERE conversation_id=? AND client_key=? AND attempt_key=?",
            reply, conversation, key, attempt);
    }

    public void releaseTurn(long conversation, String key, String attempt) {
        db.update("UPDATE ai_chat_turns SET lease_until=now() WHERE conversation_id=? AND client_key=? AND attempt_key=? AND response_id IS NULL",
            conversation, key, attempt);
    }

    public void saveToolReceipt(long message, String tool, String status, int httpStatus, long elapsedMillis) {
        db.update("INSERT INTO ai_tool_calls(message_id,tool_name,status,arguments,result)"
            + " VALUES (?,?,?,CAST('{}' AS jsonb),jsonb_build_object('httpStatus',CAST(? AS integer),'elapsedMillis',CAST(? AS bigint)))",
            message, tool, status, httpStatus, elapsedMillis);
    }

    public void clearConversationData(long conversation, long customer) {
        db.update("""
            DELETE FROM ai_tool_calls
            WHERE message_id IN (SELECT id FROM ai_messages WHERE conversation_id=?)
               OR draft_id IN (SELECT id FROM booking_drafts WHERE conversation_id=? AND customer_id=?)
            """, conversation, conversation, customer);
        db.update("DELETE FROM ai_chat_turns WHERE conversation_id=?", conversation);
        db.update("DELETE FROM ai_messages WHERE conversation_id=?", conversation);
        db.update("DELETE FROM booking_drafts WHERE conversation_id=? AND customer_id=?", conversation, customer);
        db.update("UPDATE ai_conversations SET status='ENDED',ended_at=COALESCE(ended_at,now()) WHERE id=?", conversation);
    }
}
