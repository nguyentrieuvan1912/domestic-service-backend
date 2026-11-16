package com.domesticservice.ai.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AI_SEMANTIC_VERIFY", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5551/postgres",
    "spring.datasource.username=postgres", "spring.datasource.password=ai_verify_only",
    "clients.catalog.base-url=http://127.0.0.1:8082",
    "OLLAMA_BASE_URL=http://127.0.0.1:11434", "ai.knowledge.indexer.enabled=false"
})
class CatalogSemanticPostgresTests {
    private static final HttpServer IDENTITY = identity();
    @Autowired CatalogSemanticRecommendations recommendations;
    @Autowired AiBusinessTools tools;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired AiProvider provider;
    @Autowired AiConversationService conversations;
    @Autowired AiBookingDraftService drafts;
    @Autowired OllamaEmbeddings embeddings;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void identityUrl(DynamicPropertyRegistry registry) {
        registry.add("clients.identity.base-url", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
    }

    @AfterAll static void stopIdentity() { IDENTITY.stop(0); }

    @Test
    void rankedServicesHaveMeasuredRecallAgainstCurrentKeywordSearch() throws Exception {
        var results = new ArrayList<Map<String,Object>>();
        try(var input=getClass().getResourceAsStream("/ai/semantic-service-needs.json")) {
            var cases=json.readTree(input);
            assertEquals(40,cases.size());
            for(var example:cases) {
                String query=example.path("query").asText();
                var expected=example.path("types").valueStream().map(node->node.asText()).toList();
                long started=System.nanoTime();
                var response=recommendations.recommend(new CatalogSemanticRecommendations.Query(query,null,null,null));
                var actual=response.candidates().stream().map(CatalogSemanticRecommendations.Candidate::serviceType).toList();
                var baseline=json.valueToTree(tools.call("search_services",json.valueToTree(Map.of("query",query)),""));
                boolean hit=actual.stream().anyMatch(expected::contains);
                boolean keywordHit=baseline.path("items").valueStream().anyMatch(item->expected.contains(item.path("serviceType").asText()));
                results.add(Map.of("id",example.path("id").asText(),"query",query,"expected",expected,
                    "hitAt3",hit,"keywordHit",keywordHit,"top1",!actual.isEmpty()&&expected.contains(actual.getFirst()),
                    "status",response.status(),"candidates",response.candidates(),"elapsedMs",(System.nanoTime()-started)/1000000));
                System.out.println("Semantic "+example.path("id").asText()+": "+hit+" "+actual);
            }
        }
        long hits=results.stream().filter(row->Boolean.TRUE.equals(row.get("hitAt3"))).count();
        long baseline=results.stream().filter(row->Boolean.TRUE.equals(row.get("keywordHit"))).count();
        long top1=results.stream().filter(row->Boolean.TRUE.equals(row.get("top1"))).count();
        Path output=Path.of("target","ai-semantic","results.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(
            Map.of("cases",results.size(),"hitsAt3",hits,"top1",top1,"keywordHits",baseline,"results",results)));
        assertTrue(hits>=34,"Expected at least 34/40 matches in top 3, got "+hits);
        assertTrue(hits>=baseline,"Embedding retrieval must preserve or improve keyword recall.");
    }

    @Test
    void packagesRespectAreaStaffAndDurationAndReuseStoredVectors() {
        var query=new CatalogSemanticRecommendations.Query("Gợi ý gói tổng vệ sinh nhà 70m2 trước Tết",new BigDecimal("70"),2,240);
        var result=recommendations.recommend(query);
        assertFalse(result.candidates().isEmpty(),result.toString());
        for(var candidate:result.candidates()) {
            for(var pack:candidate.packages()) {
                assertTrue(pack.staffCount()<=2);
                assertTrue(pack.durationMinutes()<=240);
                assertTrue(pack.quantity()>=1);
                assertTrue(pack.embeddingId()>0);
                assertEquals(64,pack.sourceHash().length());
                if(candidate.areaBased()) {
                    var catalog = new com.domesticservice.common.web.ServiceHttpClient("http://127.0.0.1:8082");
                    var options=json.valueToTree(catalog.get("/api/v1/catalog/services/"+candidate.serviceId()
                        +"/recommendation?areaM2=70&maxStaffCount=2&maxDurationMinutes=240",null)).path("options");
                    assertTrue(options.valueStream().anyMatch(option->option.path("packageId").asLong()==pack.packageId()
                        && option.path("requiredStaffCount").asInt()==pack.staffCount()
                        && option.path("quantity").asInt()==pack.quantity()));
                }
            }
        }
        long count=db.queryForObject("SELECT count(*) FROM catalog_semantic_vectors",Long.class);
        recommendations.recommend(query);
        assertEquals(count,db.queryForObject("SELECT count(*) FROM catalog_semantic_vectors",Long.class));
        var noFit=recommendations.recommend(new CatalogSemanticRecommendations.Query("Nhờ người dọn dẹp",null,1,1));
        assertEquals("NO_FIT",noFit.status());
        assertTrue(noFit.candidates().isEmpty());
    }

    @Test
    void adviceDoesNotCreateDraftAndCardsReferenceTheSelectedService() {
        var reply=provider.reply(List.of(Map.of("role","USER","content",
            "Gợi ý dịch vụ giúp mua rau thịt tươi ở siêu thị và giao về nhà kèm hóa đơn")),"");
        assertTrue(reply.draftChanges().isEmpty());
        assertFalse(reply.suggestions().isEmpty(),reply.toString());
        for(var card:reply.suggestions()) {
            assertNotNull(card.get("serviceId"));
            assertNotNull(card.get("sourceHash"));
            if("PACKAGE".equals(card.get("type"))) {
                var detail=json.valueToTree(tools.call("get_service_details",
                    json.valueToTree(Map.of("serviceId",Long.parseLong(card.get("serviceId").toString()))),""));
                assertTrue(detail.path("packages").valueStream().anyMatch(pack->
                    Long.toString(pack.path("id").asLong()).equals(card.get("referenceId"))));
            }
        }
        var unknown=recommendations.recommend(new CatalogSemanticRecommendations.Query(
            "Tối ưu quỹ đạo tàu vũ trụ và xây động cơ tên lửa trên sao Hỏa",null,null,null));
        assertTrue(unknown.candidates().isEmpty(),unknown.toString());
        var vague=recommendations.recommend(new CatalogSemanticRecommendations.Query("Tôi cần người giúp đỡ",null,null,null));
        assertEquals("NEEDS_DETAILS",vague.status());
        assertTrue(vague.candidates().isEmpty());
        assertEquals("NEEDS_DETAILS", recommendations.recommend(
            new CatalogSemanticRecommendations.Query("Tư vấn dịch vụ", null, null, null)).status());
    }

    @Test
    void httpAccessAndSavedSuggestionsCanBeReplayedAndSelectedAsDraft() throws Exception {
        var http = HttpClient.newHttpClient();
        String endpoint = "http://127.0.0.1:" + port + "/api/v1/ai-assistant/recommendations";
        var request = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"Gợi ý dịch vụ mua thực phẩm\"}")).build();
        assertEquals(401, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        var adminRequest = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "application/json")
            .header("Authorization", "Bearer admin")
            .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"Gợi ý dịch vụ mua thực phẩm\"}")).build();
        assertEquals(403, http.send(adminRequest, HttpResponse.BodyHandlers.ofString()).statusCode());
        String token = "Bearer owner";
        long conversation = ((Number) conversations.create(token).get("id")).longValue();
        var body = Map.<String,Object>of("content", "Gợi ý dịch vụ đi siêu thị mua thực phẩm theo danh sách và giao về nhà", "clientKey", UUID.randomUUID().toString());
        var response = conversations.send(token, conversation, body);
        assertEquals(response, conversations.send(token, conversation, body));
        var stored = json.valueToTree(conversations.messages(token, conversation).getLast());
        assertFalse(stored.path("suggestions").isEmpty());
        assertNull(drafts.get(token, conversation).get("id"));
        var pack = stored.path("suggestions").valueStream().filter(card -> "PACKAGE".equals(card.path("type").asText())).findFirst().orElseThrow();
        var selected = drafts.patch(token, conversation, new AiBookingDraftService.Patch(null, null, Map.of(
            "serviceId", Long.parseLong(pack.path("serviceId").asText()), "packageId", Long.parseLong(pack.path("referenceId").asText()),
            "quantity", pack.path("quantity").asLong(), "requiredStaffCount", pack.path("requiredStaffCount").asLong(), "mode", "B")));
        assertEquals(pack.path("referenceId").asText(), json.valueToTree(selected).path("data").path("packageId").asText());
        assertEquals(2, conversations.messages(token, conversation).size());
        conversations.clear(token, conversation);
        assertTrue(conversations.messages(token, conversation).isEmpty());
        assertNull(drafts.get(token, conversation).get("id"));
    }

    @Test
    void changedAndInactiveCatalogEntriesDoNotReuseStaleRecommendations() throws Exception {
        var live = new com.domesticservice.common.web.ServiceHttpClient("http://127.0.0.1:8082");
        var detail = live.get("/api/v1/catalog/services/14", null);
        var service = json.valueToTree(detail).path("service");
        var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        var changed = new java.util.concurrent.atomic.AtomicBoolean(false);
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/api/v1/catalog/services", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Object output;
            int status = 200;
            if (path.equals("/api/v1/catalog/services")) {
                output = Map.of("items", enabled.get() ? List.of(service) : List.of(), "totalPages", 1);
            } else if (enabled.get()) {
                var updated = json.readTree(json.writeValueAsString(detail));
                if (changed.get()) ((tools.jackson.databind.node.ObjectNode) updated.path("service"))
                    .put("description", "Đi chợ hộ, chọn thực phẩm tươi theo ngân sách, cập nhật nguồn nội dung.");
                output = updated;
            } else {
                output = Map.of("detail", "Inactive service");
                status = 404;
            }
            byte[] bytes = json.writeValueAsString(output).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        peer.start();
        try {
            var local = new CatalogSemanticRecommendations(db, embeddings, json,
                "http://127.0.0.1:" + peer.getAddress().getPort(), false);
            var query = new CatalogSemanticRecommendations.Query("Mua rau thịt tươi ở siêu thị theo ngân sách", null, null, null);
            var initial = local.recommend(query).candidates().getFirst();
            changed.set(true);
            var refreshed = local.recommend(query).candidates().getFirst();
            assertNotEquals(initial.sourceHash(), refreshed.sourceHash());
            assertNotEquals(initial.embeddingId(), refreshed.embeddingId());
            enabled.set(false);
            assertTrue(local.recommend(query).candidates().isEmpty());
        } finally { peer.stop(0); }
    }

    private static HttpServer identity() {
        try {
            var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            peer.createContext("/api/v1/identity/auth/me", exchange -> {
                String role = "Bearer admin".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? "ADMIN" : "CUSTOMER";
                byte[] bytes = ("{\"id\":11,\"role\":\"" + role + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var body = exchange.getResponseBody()) { body.write(bytes); }
            });
            peer.createContext("/api/v1/identity/customer/addresses", exchange -> {
                byte[] bytes = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var body = exchange.getResponseBody()) { body.write(bytes); }
            });
            peer.start();
            return peer;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
