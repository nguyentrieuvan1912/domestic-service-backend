package com.domesticservice.ai;

import com.domesticservice.ai.service.AiConversationService;
import com.domesticservice.ai.service.AiBookingDraftService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_CHAT_POSTGRES_VERIFY", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "ai.knowledge.indexer.enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5551/postgres",
    "spring.datasource.username=postgres", "spring.datasource.password=ai_verify_only"
})
class AiChatPostgresTests {
    private static final HttpServer PEER = startPeer();
    private static final CountDownLatch REQUEST_RECEIVED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_REPLY = new CountDownLatch(1);
    private static final java.util.concurrent.atomic.AtomicBoolean FAIL_NEXT =
        new java.util.concurrent.atomic.AtomicBoolean();
    @Autowired AiConversationService service;
    @Autowired AiBookingDraftService drafts;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @DynamicPropertySource
    static void peerProperties(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + PEER.getAddress().getPort();
        registry.add("clients.identity.base-url", () -> url);
        registry.add("clients.catalog.base-url", () -> url);
        registry.add("OLLAMA_BASE_URL", () -> url);
    }

    @AfterAll
    static void stopPeer() {
        PEER.stop(0);
    }

    @Test
    void customerChatHidesInternalGuidanceInNewAndPreviouslyStoredReplies() {
        String token = "Bearer owner";
        long id = conversation();
        String guidance = "Mở chi tiết đơn để gửi yêu cầu hỗ trợ.";
        String internal = " Bạn có thể kiểm tra bằng cách gọi công cụ [get_current_promotions].\n\n"
            + "Hướng dẫn tham khảo: Chính sách hỗ trợ (bản 3)";
        var request = request("INTERNAL_GUIDANCE_TEST");
        var reply = service.send(token, id, request);
        assertEquals(guidance, service.messages(token, id).getLast().get("content"));

        long replyId = ((Number) reply.get("id")).longValue();
        assertEquals(guidance, jdbc.queryForObject("SELECT content FROM ai_messages WHERE id=?", String.class, replyId));
        jdbc.update("UPDATE ai_messages SET content=? WHERE id=?", guidance + internal, replyId);
        assertEquals(guidance, service.messages(token, id).getLast().get("content"));
        assertEquals(reply, service.send(token, id, request));

        jdbc.update("UPDATE ai_messages SET content=? WHERE conversation_id=? AND sender_type='USER'", internal, id);
        assertEquals(internal, service.messages(token, id).getFirst().get("content"));
    }

    @Test
    void promotionQuestionReadsCatalogAndShowsOnlyUsableOffers() {
        String token = "Bearer owner";
        long id = conversation();
        var result = service.send(token, id, request("Khuyến mãi & mã giảm giá hôm nay có gì?"));
        String answer = service.messages(token, id).getLast().get("content").toString();
        assertTrue(answer.contains("**NOW10** · Giảm 10% · Vệ sinh nhà theo giờ"), answer);
        assertTrue(answer.contains("**OWN5**"), answer);
        assertFalse(answer.contains("FUTURE15"), answer);
        assertFalse(answer.contains("EXPIRED5"), answer);
        assertFalse(answer.contains("USED5"), answer);
        assertFalse(result.containsKey("bookingDraft"));
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM ai_tool_calls t JOIN ai_messages m ON m.id=t.message_id
            WHERE m.conversation_id=? AND t.tool_name='get_current_promotions' AND t.status='SUCCEEDED'
            """, Integer.class, id));
    }

    @Test
    void retryReturnsSameSavedReplyAndChecksOwnership() {
        long id = conversation();
        var request = request("Tôi muốn dọn nhà.");
        var reply = service.send("Bearer owner", id, request);
        assertEquals(reply, service.send("Bearer owner", id, request));
        var messages = service.messages("Bearer owner", id);
        assertEquals(2, messages.size());
        assertEquals(reply.get("id"), messages.getLast().get("id"));
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> service.messages("Bearer other", id)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.send("Bearer owner", id,
                Map.of("content", "Nội dung khác", "clientKey", request.get("clientKey"))))
            .getStatusCode().value());
    }

    @Test
    void bookingAnotherCatalogServiceKeepsSeparateDraftsAndConsultationCollapsesThem() {
        String token = "Bearer owner";
        long id = conversation();
        service.send(token, id, request("AC_SINGLE_TEST"));
        var previous = drafts.get(token, id);
        drafts.patch(token, id, new AiBookingDraftService.Patch(
            ((Number) previous.get("id")).longValue(), ((Number) previous.get("version")).longValue(),
            Map.of("addressId", 812, "promotionCode", "AC_ONLY")));

        var reply = service.send(token, id, request("Tôi muốn đặt vệ sinh nhà cửa"));
        var card = (Map<?, ?>) reply.get("bookingDraft");
        assertNotNull(card);
        assertNotEquals(previous.get("id"), card.get("id"));
        var data = (Map<?, ?>) card.get("data");
        assertEquals(2L, ((Number) data.get("serviceId")).longValue());
        assertEquals(22L, ((Number) data.get("packageId")).longValue());
        assertFalse(data.containsKey("addressId"));
        assertFalse(data.containsKey("promotionCode"));
        assertTrue(((java.util.List<?>) card.get("missingSlots")).contains("answers.floors"));
        assertTrue(service.messages(token, id).getLast().get("content").toString().contains("Vệ sinh nhà theo giờ"));

        var panel = drafts.list(token, id);
        assertEquals(card.get("id"), panel.get("expandedDraftId"));
        assertEquals(2, ((java.util.List<?>) panel.get("drafts")).size());
        long previousId = ((Number) previous.get("id")).longValue();
        var original = drafts.get(token, id, previousId);
        var originalData = (Map<?, ?>) original.get("data");
        assertEquals(812, originalData.get("addressId"));
        assertEquals("AC_ONLY", originalData.get("promotionCode"));
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> drafts.get("Bearer other", id, previousId)).getStatusCode().value());

        var consultation = request("Chính sách bảo hiểm là gì?");
        service.send(token, id, consultation);
        var collapsed = drafts.list(token, id);
        assertNull(collapsed.get("expandedDraftId"));
        assertEquals(panel.get("drafts"), collapsed.get("drafts"));

        var switched = service.send(token, id, request("Chuyển sang vệ sinh máy lạnh"));
        var resumed = (Map<?, ?>) switched.get("bookingDraft");
        var ac = (Map<?, ?>) resumed.get("data");
        assertEquals(previousId, resumed.get("id"));
        assertEquals(5L, ((Number) ac.get("serviceId")).longValue());
        assertEquals(12L, ((Number) ac.get("packageId")).longValue());
        assertEquals(1L, ((Number) ac.get("requiredStaffCount")).longValue());
        assertEquals("AC_ONLY", ac.get("promotionCode"));
        assertEquals(previousId, drafts.list(token, id).get("expandedDraftId"));
        service.send(token, id, consultation);
        assertEquals(previousId, drafts.list(token, id).get("expandedDraftId"));
    }

    @Test
    void clearRemovesStoredChatAndDraftsAndIsSafeToRetry() {
        String token = "Bearer owner";
        long id = conversation();
        long otherConversation = conversation();
        var draftTurn = request("CREATE_DRAFT_TEST");
        service.send(token, id, draftTurn);
        service.send(token, otherConversation, request("CREATE_DRAFT_TEST"));
        long draftId = ((Number) drafts.get(token, id).get("id")).longValue();
        jdbc.update("""
            INSERT INTO booking_drafts (conversation_id, customer_id, booking_id, status, expires_at, confirmed_at)
            VALUES (?, 11, 900, 'CONFIRMED', now() + interval '1 hour', now())
            """, id);
        jdbc.update("UPDATE ai_tool_calls SET draft_id=? WHERE message_id IN (SELECT id FROM ai_messages WHERE conversation_id=?)",
            draftId, id);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.clear("Bearer other", id))
            .getStatusCode().value());
        assertFalse(service.messages(token, id).isEmpty());

        service.clear(token, id);
        service.clear(token, id);
        assertTrue(service.messages(token, id).isEmpty());
        assertNull(drafts.get(token, id).get("id"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM booking_drafts WHERE conversation_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_chat_turns WHERE conversation_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_tool_calls WHERE draft_id=?", Integer.class, draftId));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.send(token, id, draftTurn))
            .getStatusCode().value());
        assertNotNull(drafts.get(token, otherConversation).get("id"));
    }

    @Test
    void providerFailureLeavesNoMessagesAndAllowsRetry() {
        long id = conversation();
        var request = request("Hướng dẫn sử dụng ứng dụng.");
        FAIL_NEXT.set(true);
        assertEquals(503, assertThrows(ResponseStatusException.class,
            () -> service.send("Bearer owner", id, request)).getStatusCode().value());
        assertTrue(service.messages("Bearer owner", id).isEmpty());
        service.send("Bearer owner", id, request);
        assertEquals(2, service.messages("Bearer owner", id).size());
    }

    @Test
    void providerWaitDoesNotHoldConversationLock() throws Exception {
        long id = conversation();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = pool.submit(() -> service.send("Bearer owner", id, request("BLOCK_TEST")));
            try {
                assertTrue(REQUEST_RECEIVED.await(5, TimeUnit.SECONDS));
                var conflict = pool.submit(() -> assertThrows(ResponseStatusException.class,
                    () -> service.send("Bearer owner", id, request("Tin khác"))));
                assertEquals(409, conflict.get(3, TimeUnit.SECONDS).getStatusCode().value());
            } finally {
                RELEASE_REPLY.countDown();
            }
            pending.get(5, TimeUnit.SECONDS);
        }
        assertEquals(2, service.messages("Bearer owner", id).size());
    }

    @Test
    void ollamaToolRoundTripSavesReceiptWithoutSendingCustomerTokenToModel() {
        long id = conversation();
        service.send("Bearer owner", id, request("ADDRESS_TOOL_TEST"));
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM ai_tool_calls t JOIN ai_messages m ON m.id=t.message_id
            WHERE m.conversation_id=? AND t.tool_name='get_customer_addresses'
              AND t.status='SUCCEEDED' AND t.result->>'httpStatus'='200'
            """, Integer.class, id));
    }

    @Test
    void draftPersistsPartialEditsAndRejectsStaleVersionsAndOtherCustomers() {
        long conversation = conversation();
        String token = "Bearer owner";
        var created = drafts.patch(token, conversation, new AiBookingDraftService.Patch(null, null,
            Map.of("serviceId", 1, "addressId", 812, "packageId", 2)));
        long id = (long) created.get("id");
        long version = (long) created.get("version");
        var updated = drafts.patch(token, conversation, new AiBookingDraftService.Patch(id, version,
            Map.of("quantity", 3)));
        assertEquals(updated.get("version"), drafts.get(token, conversation).get("version"));
        assertEquals(3, ((Number) ((Map<?, ?>) drafts.get(token, conversation).get("data")).get("quantity")).intValue());
        assertEquals(2, ((Number) ((Map<?, ?>) updated.get("data")).get("packageId")).intValue());
        assertTrue(((java.util.List<?>) updated.get("missingSlots")).contains("answers.rooms"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
            drafts.patch(token, conversation, new AiBookingDraftService.Patch(id, version, Map.of("quantity", 2)))
        ).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () ->
            drafts.get("Bearer other", conversation)).getStatusCode().value());
    }

    @Test
    void chatPersistsDraftAndRetryDoesNotRepeatTheUpdate() {
        long conversation = conversation();
        String token = "Bearer owner";
        var create = request("CREATE_DRAFT_TEST");
        var sent = service.send(token, conversation, create);
        var initial = drafts.get(token, conversation);
        assertNotNull(initial.get("id"));
        var json = new ObjectMapper();
        assertEquals(json.readTree(json.writeValueAsString(initial)),
            json.readTree(json.writeValueAsString(sent.get("bookingDraft"))));
        assertEquals(sent.get("messages"), service.send(token, conversation, create).get("messages"));
        assertEquals(initial.get("version"), drafts.get(token, conversation).get("version"));
        service.send(token, conversation, request("CHANGE_QUANTITY_TEST"));
        var data = (Map<?, ?>) drafts.get(token, conversation).get("data");
        assertEquals(3, ((Number) data.get("quantity")).intValue());
        assertEquals(2, ((Number) data.get("packageId")).intValue());
        assertEquals(812, ((Number) data.get("addressId")).intValue());
        assertEquals(4, service.messages(token, conversation).size());
    }

    private long conversation() {
        return ((Number) service.create("Bearer owner").get("id")).longValue();
    }

    @Test
    void airConditionerChatDefaultsAndOnlyAsksAboutStaffForMultipleMachines() throws Exception {
        String token = "Bearer owner";
        long id = conversation();
        var http = java.net.http.HttpClient.newHttpClient();
        var json = new ObjectMapper();
        var endpoint = "http://127.0.0.1:" + port + "/api/v1/ai-assistant/conversations/" + id;
        var sent = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint + "/messages"))
            .header("Authorization", token).header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request("AC_SINGLE_TEST"))))
            .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, sent.statusCode(), sent.body());
        var card = json.readTree(sent.body()).path("bookingDraft");
        assertTrue(card.path("id").asLong() > 0);
        assertEquals("Vệ sinh máy lạnh", card.path("choices").path("service").path("name").asText());
        var reopened = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint + "/booking-draft"))
            .header("Authorization", token).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reopened.statusCode(), reopened.body());
        assertEquals(card, json.readTree(reopened.body()));
        var single = drafts.get(token, id);
        assertEquals(Map.of("serviceId", 5, "packageId", 12, "mode", "B", "quantity", 1,
            "requiredStaffCount", 1), single.get("data"));
        assertTrue(service.messages(token, id).getLast().get("content").toString().toLowerCase(java.util.Locale.ROOT).contains("bấm xác nhận"));

        service.send(token, id, request("AC_MULTIPLE_TEST"));
        var multiple = (Map<?, ?>) drafts.get(token, id).get("data");
        assertEquals(3, multiple.get("quantity"));
        assertFalse(multiple.containsKey("requiredStaffCount"));
        assertTrue(service.messages(token, id).getLast().get("content").toString().contains("song song"));

        service.send(token, id, request("AC_STAFF_TEST"));
        var ready = (Map<?, ?>) drafts.get(token, id).get("data");
        assertEquals(3, ready.get("quantity"));
        assertEquals(2, ready.get("requiredStaffCount"));
        assertEquals("B", ready.get("mode"));
        String prompt = service.messages(token, id).getLast().get("content").toString();
        assertTrue(prompt.toLowerCase(java.util.Locale.ROOT).contains("bấm xác nhận"));
        assertTrue(prompt.contains("Nếu biết"));
        assertTrue(prompt.contains("Loại máy lạnh"));
        assertTrue(prompt.contains("Âm trần cassette"));
        assertTrue(prompt.contains("Vị trí dàn nóng"));
        assertTrue(prompt.contains("Cần thang cao"));

        var persisted = drafts.get(token, id);
        var answers = Map.of("airconType", "Âm trần cassette", "access", "Cần thang cao");
        drafts.patch(token, id, new AiBookingDraftService.Patch(
            ((Number) persisted.get("id")).longValue(), ((Number) persisted.get("version")).longValue(),
            Map.of("answers", answers)));
        var refreshed = service.send(token, id, request("AC_STAFF_TEST"));
        var updatedCard = json.valueToTree(refreshed).path("bookingDraft");
        assertEquals(json.valueToTree(answers), updatedCard.path("data").path("answers"));
        String completed = service.messages(token, id).getLast().get("content").toString();
        assertFalse(completed.contains("Loại máy lạnh"));
        assertFalse(completed.contains("Vị trí dàn nóng"));
    }

    private static Map<String, Object> request(String message) {
        return Map.of("content", message, "clientKey", UUID.randomUUID().toString());
    }

    private static HttpServer startPeer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(command -> Thread.startVirtualThread(command));
            server.createContext("/api/v1/identity/auth/me", exchange -> {
                long id = "Bearer owner".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 11 : 12;
                byte[] body = ("{\"id\":" + id + ",\"role\":\"CUSTOMER\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/api/v1/identity/customer/addresses", exchange -> {
                boolean owner = "Bearer owner".equals(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, owner ? 200 : 403, "[{\"id\":812}]");
            });
            server.createContext("/api/v1/catalog/promotions", exchange -> respond(exchange, 200, """
                [{"code":"NOW10","discountType":"PERCENTAGE","discountValue":10,"isActive":true,
                  "endDate":"2099-01-01T00:00:00Z","serviceIds":[2]},
                 {"code":"FUTURE15","discountType":"PERCENTAGE","discountValue":15,"isActive":false,"serviceIds":[]}]
                """));
            server.createContext("/api/v1/catalog/customer/vouchers", exchange -> {
                boolean owner = "Bearer owner".equals(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, owner ? 200 : 403, owner ? """
                    [{"code":"OWN5","name":"Voucher riêng 5%","expires_at":"2099-01-01T00:00:00Z","used_count":0},
                     {"code":"EXPIRED5","expires_at":"2020-01-01T00:00:00Z","used_count":0},
                     {"code":"USED5","expires_at":"2099-01-01T00:00:00Z","used_count":1}]
                    """ : "{}");
            });
            server.createContext("/api/v1/catalog/services/1", exchange -> respond(exchange, 200, """
                {"packages":[{"id":2}],"requirements":[{"fieldKey":"rooms","required":true}]}
                """));
            server.createContext("/api/v1/catalog/services", exchange -> respond(exchange, 200, """
                {"items":[{"id":2,"name":"Vệ sinh nhà theo giờ"},{"id":5,"name":"Vệ sinh máy lạnh"}]}
                """));
            server.createContext("/api/v1/catalog/services/2", exchange -> respond(exchange, 200, """
                {"service":{"id":2,"name":"Vệ sinh nhà theo giờ","serviceType":"CLEANING_HOURLY","priceUnit":"HOUR"},
                 "packages":[{"id":22,"defaultStaffCount":2,"basePrice":200000}],
                 "requirements":[{"fieldKey":"floors","required":true}]}
                """));
            server.createContext("/api/v1/catalog/services/5", exchange -> respond(exchange, 200, """
                {"service":{"id":5,"name":"Vệ sinh máy lạnh","serviceType":"AC_CLEANING","priceUnit":"ITEM"},
                 "packages":[{"id":13,"defaultStaffCount":1,"basePrice":200000},
                             {"id":12,"defaultStaffCount":1,"basePrice":150000},
                             {"id":14,"defaultStaffCount":2,"basePrice":100000}],
                 "requirements":[
                   {"id":26,"fieldKey":"airconType","label":"Loại máy lạnh","fieldType":"SELECT","required":true,
                    "options":["Treo tường","Âm trần cassette","Áp trần"],"displayOrder":10},
                   {"id":25,"fieldKey":"access","label":"Vị trí dàn nóng","fieldType":"SELECT","required":true,
                    "options":["Dễ tiếp cận","Cần thang cao"],"displayOrder":20}]}
                """));
            server.createContext("/api/chat", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var inputMessages = new ObjectMapper().readTree(request).path("messages");
                String latest = inputMessages.path(inputMessages.size() - 1).path("content").asText();
                if (new ObjectMapper().readTree(request).has("format")) {
                    respond(exchange, 200, """
                        {"done":true,"message":{"role":"assistant","content":"{}"}}
                        """);
                    return;
                }
                String arguments = switch (latest) {
                    case "CREATE_DRAFT_TEST" -> "{\"serviceId\":1,\"packageId\":2,\"addressId\":812}";
                    case "CHANGE_QUANTITY_TEST", "AC_MULTIPLE_TEST" -> "{\"quantity\":3}";
                    case "AC_SINGLE_TEST" -> "{\"serviceId\":5,\"quantity\":1,\"packageId\":13,\"requiredStaffCount\":2,\"mode\":\"A\"}";
                    case "AC_STAFF_TEST" -> "{\"requiredStaffCount\":2}";
                    default -> null;
                };
                if (arguments != null) {
                    respond(exchange, 200, """
                        {"done":true,"message":{"role":"assistant","tool_calls":[
                          {"function":{"name":"propose_booking_draft","arguments":%s}}]}}
                        """.formatted(arguments));
                    return;
                }
                if (request.contains("ADDRESS_TOOL_TEST")) {
                    var json = new ObjectMapper().readTree(request);
                    var messages = json.path("messages");
                    var last = messages.path(messages.size() - 1);
                    if (exchange.getRequestHeaders().containsKey("Authorization") || request.contains("Bearer owner")) {
                        respond(exchange, 400, "{}");
                    } else if ("user".equals(last.path("role").asText())) {
                        respond(exchange, 200, """
                            {"done":true,"message":{"role":"assistant","content":"","tool_calls":[
                              {"function":{"name":"get_customer_addresses","arguments":{}}}]}}
                            """);
                    } else {
                        boolean valid = "tool".equals(last.path("role").asText())
                            && "get_customer_addresses".equals(last.path("tool_name").asText())
                            && last.path("content").asText().contains("812")
                            && messages.path(messages.size() - 2).path("tool_calls").size() == 1;
                        respond(exchange, valid ? 200 : 400, """
                            {"done":true,"message":{"role":"assistant","content":"Bạn có một địa chỉ đã lưu."}}
                            """);
                    }
                    return;
                }
                if (request.contains("BLOCK_TEST")) {
                    REQUEST_RECEIVED.countDown();
                    try {
                        RELEASE_REPLY.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if ("INTERNAL_GUIDANCE_TEST".equals(latest)) {
                    respond(exchange, 200, """
                        {"done":true,"message":{"role":"assistant","content":"Mở chi tiết đơn để gửi yêu cầu hỗ trợ. Bạn có thể kiểm tra bằng cách gọi công cụ [get_current_promotions].\\n\\nHướng dẫn tham khảo: Chính sách hỗ trợ (bản 3)"}}
                        """);
                    return;
                }
                respond(exchange, FAIL_NEXT.getAndSet(false) ? 503 : 200, """
                    {"done":true,"message":{"role":"assistant","content":"Tôi có thể tư vấn dịch vụ dọn nhà."}}
                    """);
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(body);
        }
    }
}
