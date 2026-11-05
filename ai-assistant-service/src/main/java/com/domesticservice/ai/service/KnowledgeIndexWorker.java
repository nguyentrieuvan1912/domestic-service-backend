package com.domesticservice.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableScheduling
class KnowledgeIndexWorker {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final OllamaEmbeddings embeddings;
    private final ObjectMapper json;
    private final boolean enabled;

    KnowledgeIndexWorker(JdbcTemplate db, PlatformTransactionManager manager, OllamaEmbeddings embeddings,
                         ObjectMapper json, @Value("${ai.knowledge.indexer.enabled:false}") boolean enabled) {
        this.db = db;
        this.transactions = new TransactionTemplate(manager);
        this.embeddings = embeddings;
        this.json = json;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 30000)
    void tick() {
        if (!enabled) return;
        try { runNext(); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Knowledge indexing queue temporarily unavailable.");
        }
    }

    boolean runNext() {
        String token = UUID.randomUUID().toString();
        var claimed = db.queryForList("""
            UPDATE knowledge_documents SET index_status='INDEXING',index_token=?,index_started_at=now(),index_error=NULL
            WHERE id=(SELECT id FROM knowledge_documents
              WHERE status='ACTIVE' AND approval_status='APPROVED'
              AND (index_status='PENDING'
                OR (index_status='INDEXING' AND index_started_at<now()-interval '5 minutes')
                OR (index_status='READY' AND indexed_model<>?))
              ORDER BY updated_at,id LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING id,title,content,version
            """, token, embeddings.model);
        if (claimed.isEmpty()) return false;
        var document = claimed.getFirst();
        long id = ((Number) document.get("id")).longValue();
        long version = ((Number) document.get("version")).longValue();
        try {
            var chunks = chunks(document.get("content").toString());
            var vectors = new ArrayList<List<Double>>();
            for (int offset = 0; offset < chunks.size(); offset += 8) {
                var input = chunks.subList(offset, Math.min(chunks.size(), offset + 8)).stream()
                    .map(chunk -> document.get("title") + "\n" + chunk).toList();
                vectors.addAll(embeddings.documents(input));
            }
            if (vectors.isEmpty() || vectors.stream().anyMatch(vector -> vector.size() != vectors.getFirst().size())) {
                throw new IllegalStateException("Embedding dimensions changed while indexing.");
            }
            transactions.executeWithoutResult(status -> {
                var current = db.queryForList("""
                    SELECT id FROM knowledge_documents WHERE id=? AND version=? AND index_token=?
                    AND status='ACTIVE' AND approval_status='APPROVED' FOR UPDATE
                    """, id, version, token);
                if (current.isEmpty()) return;
                db.update("DELETE FROM knowledge_chunks WHERE document_id=?", id);
                for (int index = 0; index < chunks.size(); index++) {
                    db.update("""
                        INSERT INTO knowledge_chunks(document_id,chunk_index,content,embedding,embedding_model,document_version)
                        VALUES (?,?,?,CAST(? AS jsonb),?,?)
                        """, id, index, chunks.get(index), json.writeValueAsString(vectors.get(index)), embeddings.model, version);
                }
                db.update("""
                    UPDATE knowledge_documents SET index_status='READY',indexed_version=?,indexed_model=?,
                    embedding_dimension=?,indexed_at=now(),index_error=NULL,index_token=NULL WHERE id=?
                    """, version, embeddings.model, vectors.getFirst().size(), id);
            });
        } catch (RuntimeException failure) {
            db.update("""
                UPDATE knowledge_documents SET index_status='FAILED',index_error=?,index_token=NULL
                WHERE id=? AND version=? AND index_token=?
                """, "Chưa lập chỉ mục được. Kiểm tra model embedding local rồi thử lại.", id, version, token);
        }
        return true;
    }

    static List<String> chunks(String content) {
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + 1400, content.length());
            if (end < content.length()) {
                int boundary = content.lastIndexOf(' ', end);
                if (boundary > start + 700) end = boundary;
                if (Character.isHighSurrogate(content.charAt(end - 1))) end--;
            }
            String chunk = content.substring(start, end).strip();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end == content.length()) break;
            start = Math.max(start + 1, end - 160);
            if (Character.isLowSurrogate(content.charAt(start))) start++;
        }
        return chunks;
    }
}
