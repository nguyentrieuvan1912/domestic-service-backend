package com.domesticservice.ai.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_KNOWLEDGE_VERIFY", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5551/postgres",
    "spring.datasource.username=postgres", "spring.datasource.password=ai_verify_only",
    "OLLAMA_BASE_URL=http://127.0.0.1:11434", "ai.knowledge.indexer.enabled=false"
})
class KnowledgePostgresTests {
    private static final HttpServer IDENTITY = identity();
    @Autowired KnowledgeDocuments documents;
    @Autowired KnowledgeIndexWorker worker;
    @Autowired AiCustomerKnowledge knowledge;
    @Autowired OllamaEmbeddings embeddings;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String ADMIN = "Bearer rag-admin";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("clients.identity.base-url", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
    }
    @AfterAll static void closePeer() { IDENTITY.stop(0); }
    @AfterEach void hideTestDocuments() {
        db.update("UPDATE knowledge_documents SET status='INACTIVE',index_token=NULL WHERE source_uri LIKE 'cleanmaster:rag-test/%'");
    }

    @Test
    void httpCrudApprovalHistoryOwnershipAndSemanticSearch() throws Exception {
        String root = "http://127.0.0.1:" + port + "/api/v1/ai-assistant/admin/knowledge";
        var unauthorized = http.send(HttpRequest.newBuilder(URI.create(root)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthorized.statusCode());
        var denied = http.send(HttpRequest.newBuilder(URI.create(root)).header("Authorization", "Bearer customer").GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode());
        String marker = UUID.randomUUID().toString();
        var created = post(root, new KnowledgeDocuments.Edit(null, "Hướng dẫn thú cưng " + marker,
            "cleanmaster:rag-test/" + marker, "Trước khi nhân viên đến chăm sóc thú cưng, khách chuẩn bị thức ăn, dây dắt và ghi chú thói quen của bé."));
        assertEquals(201, created.statusCode(), created.body());
        var draft = json.readTree(created.body());
        long id = draft.path("id").asLong();
        assertEquals("DRAFT", draft.path("approvalStatus").asText());
        assertFalse(knowledge.find("Chuẩn bị dây dắt cho thú cưng").stream().anyMatch(article -> article.documentId() == id));
        var approved = post(root + "/" + id + "/approve", Map.of("version", draft.path("version").asLong()));
        assertEquals(200, approved.statusCode(), approved.body());
        index(id);
        var ready = documents.detail(ADMIN, id);
        assertEquals(embeddings.model, ready.get("indexedModel"));
        var search = knowledge.search("Tôi phải sẵn sàng những gì trước buổi chăm sóc thú cưng?");
        assertEquals("HYBRID", search.mode());
        assertTrue(search.articles().stream().anyMatch(article -> article.documentId() == id), search.toString());
        var source = search.articles().stream().filter(article -> article.documentId() == id).toList();
        assertTrue(knowledge.areCurrent(source));
        documents.transition(ADMIN, id, ((Number) ready.get("version")).longValue(), "hide");
        assertFalse(knowledge.areCurrent(source));
        assertFalse(knowledge.find("Chuẩn bị thú cưng dây dắt").stream().anyMatch(article -> article.documentId() == id));
        assertEquals(3, documents.history(ADMIN, id).size());
    }

    @Test
    void editInvalidatesOldChunksAndRejectsStaleVersion() {
        var created = create("Đối soát ngân sách đi chợ", "Mua thực phẩm cần ghi ngân sách tối đa và đối chiếu hóa đơn thực tế.");
        long id = number(created, "id");
        documents.transition(ADMIN, id, number(created, "version"), "approve");
        index(id);
        var ready = documents.detail(ADMIN, id);
        var edit = new KnowledgeDocuments.Edit(number(ready, "version"), "Hướng dẫn đi chợ cập nhật",
            created.get("sourceUri").toString(), "Khi khách thay đổi danh sách thực phẩm, xác nhận lại ngân sách và giữ hóa đơn mua hàng.");
        var changed = documents.save(ADMIN, id, edit);
        assertEquals("DRAFT", changed.get("approvalStatus"));
        assertFalse(knowledge.find("Đối soát ngân sách đi chợ").stream().anyMatch(article -> article.documentId() == id));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> documents.save(ADMIN, id, edit)).getStatusCode().value());
        var noOp = documents.save(ADMIN, id, new KnowledgeDocuments.Edit(number(changed, "version"), edit.title(), edit.sourceUri(), edit.content()));
        assertEquals(changed.get("version"), noOp.get("version"));
        documents.transition(ADMIN, id, number(changed, "version"), "approve");
        index(id);
        assertEquals(1, db.queryForObject("SELECT count(DISTINCT document_version) FROM knowledge_chunks WHERE document_id=?", Integer.class, id));
        assertEquals(number(documents.detail(ADMIN, id), "version"),
            db.queryForObject("SELECT min(document_version) FROM knowledge_chunks WHERE document_id=?", Long.class, id));
    }

    @Test
    void failedEmbeddingCanBeRetriedWithoutDuplicateChunks() {
        drain();
        var created = create("Lỗi kết nối kiểm thử", "Hướng dẫn khách kiểm tra địa chỉ trước khi đặt.");
        long id = number(created, "id");
        var approved = documents.transition(ADMIN, id, number(created, "version"), "approve");
        var offline = new OllamaEmbeddings("http://127.0.0.1:1", embeddings.model);
        var failedWorker = new KnowledgeIndexWorker(db, transactions, offline, json, false);
        assertTrue(failedWorker.runNext());
        assertEquals("FAILED", documents.detail(ADMIN, id).get("indexStatus"));
        documents.transition(ADMIN, id, number(approved, "version"), "reindex");
        index(id);
        long count = db.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE document_id=?", Long.class, id);
        var ready = documents.detail(ADMIN, id);
        documents.transition(ADMIN, id, number(ready, "version"), "reindex");
        assertEquals(count, db.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE document_id=?", Long.class, id));
    }

    @Test
    void editingDuringNetworkCallDiscardsStaleIndex() throws Exception {
        drain();
        var created = create("Phiên bản đang được lập chỉ mục", "Hướng dẫn chuẩn bị dụng cụ trước khi dọn dẹp.");
        long id = number(created, "id");
        var approved = documents.transition(ADMIN, id, number(created, "version"), "approve");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var delayed = new OllamaEmbeddings("http://127.0.0.1:11434", embeddings.model) {
            @Override List<List<Double>> documents(List<String> texts) {
                var result = super.documents(texts);
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                return result;
            }
        };
        var delayedWorker = new KnowledgeIndexWorker(db, transactions, delayed, json, false);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var indexing = pool.submit(delayedWorker::runNext);
            try {
                assertTrue(entered.await(30, TimeUnit.SECONDS));
                var update = pool.submit(() -> documents.save(ADMIN, id, new KnowledgeDocuments.Edit(
                    number(approved, "version"), "Nội dung mới", created.get("sourceUri").toString(),
                    "Khách cần xác nhận lại địa chỉ đã thay đổi.")));
                assertEquals("DRAFT", update.get(3, TimeUnit.SECONDS).get("approvalStatus"));
            } finally { release.countDown(); }
            assertTrue(indexing.get(5, TimeUnit.SECONDS));
        }
        assertEquals("PENDING", documents.detail(ADMIN, id).get("indexStatus"));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE document_id=?", Integer.class, id));
    }

    @Test
    void vietnameseFaqRetrievalHasMeasuredRecallAgainstKeywordBaseline() throws Exception {
        drain();
        var fallback = new AiCustomerKnowledge(db, new OllamaEmbeddings("http://127.0.0.1:1", embeddings.model), json);
        var report = new ArrayList<Map<String, Object>>();
        try (var input = getClass().getResourceAsStream("/ai/rag-vietnamese-questions.json")) {
            var questions = json.readTree(input);
            assertEquals(40, questions.size());
            for (var item : questions) {
                String question = item.path("question").asText();
                String expected = "cleanmaster:customer-guide/" + item.path("topic").asText();
                var actual = knowledge.search(question);
                var baseline = fallback.search(question);
                assertEquals("HYBRID", actual.mode());
                boolean hit = actual.articles().stream().anyMatch(article -> expected.equals(article.sourceUri()));
                boolean keywordHit = baseline.articles().stream().anyMatch(article -> expected.equals(article.sourceUri()));
                report.add(Map.of("question", question, "expectedSource", expected, "hitAt3", hit,
                    "keywordHitAt3", keywordHit, "sources", actual.articles()));
            }
        }
        long hits = report.stream().filter(row -> Boolean.TRUE.equals(row.get("hitAt3"))).count();
        long baselineHits = report.stream().filter(row -> Boolean.TRUE.equals(row.get("keywordHitAt3"))).count();
        Path output = Path.of("target", "ai-rag", "retrieval-results.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(
            Map.of("cases", report.size(), "hitsAt3", hits, "keywordHitsAt3", baselineHits,
                "model", embeddings.model, "results", report)));
        assertTrue(hits >= 34, "Expected at least 34/40 relevant sources in top 3, got " + hits);
        assertTrue(hits >= baselineHits, "Hybrid retrieval must preserve or improve keyword recall.");
        assertTrue(knowledge.search("Tính quỹ đạo tàu thăm dò sao Diêm Vương và động cơ tên lửa")
            .articles().isEmpty(), "Unrelated astronomy needs no customer-policy citation.");
    }

    @Test
    void chunkingKeepsAllTextAndProducesBoundedOverlappingChunks() {
        String body = "Khách cần xác nhận địa chỉ và chuẩn bị dụng cụ. ".repeat(150);
        var chunks = KnowledgeIndexWorker.chunks(body);
        assertTrue(chunks.size() > 2);
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.isBlank() && chunk.length() <= 1400));
        assertTrue(chunks.getFirst().startsWith("Khách cần"));
        assertTrue(chunks.getLast().endsWith("dụng cụ."));
    }

    private Map<String, Object> create(String title, String content) {
        return documents.save(ADMIN, null, new KnowledgeDocuments.Edit(null, title,
            "cleanmaster:rag-test/" + UUID.randomUUID(), content));
    }
    private void drain() {
        for (int attempt = 0; attempt < 50 && worker.runNext(); attempt++) { /* Drain this isolated database's queue. */ }
    }
    private void index(long id) {
        for (int attempt = 0; attempt < 50; attempt++) {
            var document = documents.detail(ADMIN, id);
            if ("READY".equals(document.get("indexStatus"))) return;
            assertNotEquals("FAILED", document.get("indexStatus"), document.toString());
            assertTrue(worker.runNext(), "Document was not available to the indexer: " + document);
        }
        fail("Indexing queue did not finish.");
    }
    private HttpResponse<String> post(String url, Object body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", ADMIN)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(), HttpResponse.BodyHandlers.ofString());
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static HttpServer identity() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/identity/auth/me", exchange -> {
                String role = "Bearer rag-admin".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? "ADMIN" : "CUSTOMER";
                byte[] body = ("{\"id\":11,\"role\":\"" + role + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
