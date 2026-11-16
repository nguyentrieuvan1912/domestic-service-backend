package com.domesticservice.ai.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeDocuments {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ServiceHttpClient identity;
    private final OllamaEmbeddings embeddings;

    KnowledgeDocuments(JdbcTemplate db, PlatformTransactionManager manager, OllamaEmbeddings embeddings,
                       @Value("${clients.identity.base-url}") String identityUrl) {
        this.db = db;
        this.transactions = new TransactionTemplate(manager);
        this.embeddings = embeddings;
        identity = new ServiceHttpClient(identityUrl);
    }

    public void admin(String token) { identity.authenticate(token, "ADMIN"); }

    public List<Map<String, Object>> list(String token) {
        admin(token);
        return db.queryForList("""
            SELECT id,title,source_uri AS "sourceUri",status,version,approval_status AS "approvalStatus",
                   index_status AS "indexStatus",indexed_model AS "indexedModel",index_error AS "indexError",
                   updated_at AS "updatedAt",indexed_at AS "indexedAt",
                   (SELECT count(*) FROM knowledge_chunks c WHERE c.document_id=d.id AND c.document_version=d.version) AS "chunkCount"
            FROM knowledge_documents d ORDER BY updated_at DESC,id DESC
            """);
    }

    public Map<String, Object> detail(String token, long id) { admin(token); return read(id, false); }

    public List<Map<String, Object>> history(String token, long id) {
        admin(token);
        read(id, false);
        return db.queryForList("""
            SELECT version,title,source_uri AS "sourceUri",content,status,approval_status AS "approvalStatus",
                   actor_id AS "actorId",created_at AS "createdAt",content_hash AS "contentHash"
            FROM knowledge_document_revisions WHERE document_id=? ORDER BY version DESC
            """, id);
    }

    public Map<String, Object> save(String token, Long id, Edit edit) {
        long actor = ServiceHttpClient.userId(identity.authenticate(token, "ADMIN"));
        String title = text(edit.title(), 200, "Tiêu đề");
        String content = text(edit.content(), 50000, "Nội dung");
        String source = Objects.toString(edit.sourceUri(), "").strip();
        if (source.length() > 500 || !source.isEmpty()
            && !(source.startsWith("https://") || source.startsWith("http://") || source.startsWith("cleanmaster:"))) {
            throw bad("Nguồn tài liệu dùng địa chỉ http/https hoặc mã cleanmaster:.");
        }
        return transactions.execute(status -> {
            long document;
            if (id == null) {
                if (edit.version() != null) throw bad("Tài liệu mới chưa có phiên bản.");
                document = db.queryForObject("""
                    INSERT INTO knowledge_documents(title,content,source_uri,status,approval_status,content_hash,updated_by)
                    VALUES (?,?,?,'INACTIVE','DRAFT',?,?) RETURNING id
                    """, Long.class, title, content, source, hash(content), actor);
            } else {
                var previous = read(id, true);
                checkVersion(previous, edit.version());
                if (title.equals(previous.get("title")) && content.equals(previous.get("content"))
                    && source.equals(Objects.toString(previous.get("sourceUri"), ""))) return previous;
                document = id;
                db.update("""
                    UPDATE knowledge_documents SET title=?,content=?,source_uri=?,content_hash=?,version=version+1,
                      status='INACTIVE',approval_status='DRAFT',index_status='PENDING',index_token=NULL,index_error=NULL,
                      reviewed_at=NULL,reviewed_by=NULL,updated_at=now(),updated_by=? WHERE id=?
                    """, title, content, source, hash(content), actor, id);
            }
            revision(document, actor);
            return read(document, false);
        });
    }

    public Map<String, Object> transition(String token, long id, long version, String action) {
        long actor = ServiceHttpClient.userId(identity.authenticate(token, "ADMIN"));
        return transactions.execute(status -> {
            var current = read(id, true);
            checkVersion(current, version);
            switch (action) {
                case "approve" -> db.update("""
                    UPDATE knowledge_documents SET status='ACTIVE',approval_status='APPROVED',version=version+1,
                    index_status='PENDING',index_token=NULL,index_error=NULL,updated_at=now(),updated_by=?,
                    reviewed_by=?,reviewed_at=now() WHERE id=?
                    """, actor, actor, id);
                case "hide" -> db.update("""
                    UPDATE knowledge_documents SET status='INACTIVE',version=version+1,index_token=NULL,
                    index_status='PENDING',updated_at=now(),updated_by=? WHERE id=?
                    """, actor, id);
                case "reindex" -> {
                    if (!"APPROVED".equals(current.get("approvalStatus")) || !"ACTIVE".equals(current.get("status"))) {
                        throw bad("Duyệt tài liệu trước khi lập chỉ mục.");
                    }
                    if ("READY".equals(current.get("indexStatus")) && embeddings.model.equals(current.get("indexedModel"))
                        && Objects.equals(current.get("version"), current.get("indexedVersion"))) return current;
                    if ("INDEXING".equals(current.get("indexStatus"))) return current;
                    db.update("UPDATE knowledge_documents SET index_status='PENDING',index_error=NULL WHERE id=?", id);
                    return read(id, false);
                }
                default -> throw bad("Thao tác tài liệu không hợp lệ.");
            }
            revision(id, actor);
            return read(id, false);
        });
    }

    Map<String, Object> read(long id, boolean lock) {
        var rows = db.queryForList("""
            SELECT id,title,content,source_uri AS "sourceUri",version,status,approval_status AS "approvalStatus",
             index_status AS "indexStatus",indexed_version AS "indexedVersion",indexed_model AS "indexedModel",
             embedding_dimension AS "embeddingDimension",index_error AS "indexError",updated_at AS "updatedAt",
             indexed_at AS "indexedAt",content_hash AS "contentHash"
            FROM knowledge_documents WHERE id=?
            """ + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu.");
        return rows.getFirst();
    }

    private void revision(long id, long actor) {
        db.update("""
            INSERT INTO knowledge_document_revisions(document_id,version,title,source_uri,content,content_hash,status,approval_status,actor_id)
            SELECT id,version,title,source_uri,content,content_hash,status,approval_status,? FROM knowledge_documents WHERE id=?
            """, actor, id);
    }

    private static void checkVersion(Map<String, Object> document, Long version) {
        if (version == null || ((Number) document.get("version")).longValue() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài liệu đã thay đổi. Tải lại trước khi chỉnh sửa.");
        }
    }

    private static String text(String value, int limit, String label) {
        if (value == null || value.isBlank() || value.length() > limit) throw bad(label + " không hợp lệ hoặc vượt giới hạn.");
        return value.strip();
    }

    static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public record Edit(Long version, String title, String sourceUri, String content) {}
    public record Version(long version) {}
}
