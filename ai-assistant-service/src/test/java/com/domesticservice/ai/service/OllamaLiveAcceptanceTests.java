package com.domesticservice.ai.service;

import com.domesticservice.ai.repository.AiConversationSqlRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_CHAT_OLLAMA_VERIFY", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "ai.knowledge.indexer.enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5551/postgres",
    "spring.datasource.username=postgres", "spring.datasource.password=ai_verify_only",
    "clients.catalog.base-url=http://127.0.0.1:8082",
    "OLLAMA_BASE_URL=http://127.0.0.1:11434"
})
class OllamaLiveAcceptanceTests {
    @Autowired AiConversationSqlRepository repository;
    @Autowired PlatformTransactionManager manager;
    @Autowired AiProvider provider;
    @Autowired AiBusinessTools tools;
    @Autowired AiBookingDraftService drafts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void vietnameseConversationUsesSavedContextAndReplaysSameReply() throws Exception {
        // Isolate Identity from the live deployment. Ollama and PostgreSQL are real.
        var identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/api/v1/identity/auth/me", exchange -> {
            byte[] data = "{\"id\":11,\"role\":\"CUSTOMER\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            try (var body = exchange.getResponseBody()) { body.write(data); }
        });
        identity.start();
        try {
            var service = new AiConversationService(repository, provider, manager, drafts,
                "http://127.0.0.1:" + identity.getAddress().getPort());
            String token = "Bearer isolated-acceptance";
            long id = ((Number) service.create(token).get("id")).longValue();
            String marker = "Dọn nhà " + UUID.randomUUID().toString().substring(0, 8);
            service.send(token, id, Map.of("content", "Ghi nhớ cụm từ kiểm thử này: " + marker,
                "clientKey", UUID.randomUUID().toString()));
            var request = Map.<String, Object>of("content", "Nhắc lại chính xác cụm từ tiếng Việt tôi vừa đưa.",
                "clientKey", UUID.randomUUID().toString());
            var reply = service.send(token, id, request);
            assertEquals(reply, service.send(token, id, request));
            var saved = service.messages(token, id);
            assertEquals(4, saved.size());
            assertEquals(reply.get("id"), saved.getLast().get("id"));
            assertTrue(saved.getLast().get("content").toString().contains(marker));

            var lookup = service.send(token, id, Map.of(
                "content", "Tra danh mục thật bằng công cụ search_services với từ khóa vệ sinh và nói tên một dịch vụ có trong kết quả.",
                "clientKey", UUID.randomUUID().toString()));
            assertNotNull(lookup.get("id"));
            assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM ai_tool_calls t JOIN ai_messages m ON m.id=t.message_id
                WHERE m.conversation_id=? AND t.tool_name='search_services' AND t.status='SUCCEEDED'
                """, Integer.class, id) > 0);
            assertEquals(6, service.messages(token, id).size());

            long areaId = ((Number) service.create(token).get("id")).longValue();
            service.send(token, areaId, Map.of("content", "Nhà 70m2 nên đặt gói dọn dẹp nào?",
                "clientKey", UUID.randomUUID().toString()));
            assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM ai_tool_calls t JOIN ai_messages m ON m.id=t.message_id
                WHERE m.conversation_id=? AND t.tool_name='recommend_services' AND t.status='SUCCEEDED'
                """, Integer.class, areaId) > 0);

            long faqId = ((Number) service.create(token).get("id")).longValue();
            service.send(token, faqId, Map.of("content", "Chính sách bảo hiểm hư hại của CleanMaster",
                "clientKey", UUID.randomUUID().toString()));
            String answer = service.messages(token, faqId).getLast().get("content").toString();
            assertTrue(answer.contains("khiếu nại") || answer.contains("hỗ trợ"), answer);
            assertFalse(answer.contains("Hướng dẫn tham khảo"), answer);
            assertFalse(answer.contains("cleanmaster:customer-guide/"), answer);

            var proposal = provider.reply(java.util.List.of(Map.of("role", "USER", "content",
                "Tôi muốn đặt dịch vụ ID 5, vệ sinh 3 máy lạnh vào chiều mai. Hãy lưu thông tin vào bản nháp.")), token);
            assertEquals(3, ((Number) proposal.draftChanges().getOrDefault("quantity", 0)).intValue(), proposal.toString());
            assertFalse(proposal.draftChanges().containsKey("startsAt"), proposal.toString());

            var single = provider.reply(java.util.List.of(Map.of("role", "USER", "content",
                "Đặt giúp tôi vệ sinh một máy lạnh.")), token);
            assertEquals(1, ((Number) single.draftChanges().getOrDefault("quantity", 0)).intValue(), single.toString());
            assertEquals(5, ((Number) single.draftChanges().getOrDefault("serviceId", 0)).intValue(), single.toString());

            var weekend = provider.reply(java.util.List.of(Map.of("role", "USER", "content",
                "Tôi muốn vệ sinh 3 máy lạnh vào chiều thứ bảy")), token);
            assertEquals(5, ((Number) weekend.draftChanges().getOrDefault("serviceId", 0)).intValue(), weekend.toString());
            assertEquals(3, ((Number) weekend.draftChanges().getOrDefault("quantity", 0)).intValue(), weekend.toString());

            var catalog = tools.activeServices();
            assertFalse(catalog.isEmpty());
            var catalogJson = new tools.jackson.databind.ObjectMapper();
            var overview = tools.call("get_catalog_overview", catalogJson.valueToTree(Map.of()), token);
            var overviewData = catalogJson.valueToTree(overview);
            assertFalse(overviewData.path("categories").isEmpty());
            assertEquals(catalog.size(), overviewData.path("services").size());
            for (var selected : catalog) {
                String serviceName = selected.get("name").toString();
                var available = provider.reply(java.util.List.of(
                    Map.of("role", "ASSISTANT", "content", "Không có dịch vụ này trong danh mục."),
                    Map.of("role", "USER", "content", "Có dịch vụ " + serviceName + " không?")), token);
                assertTrue(available.text().contains(serviceName), available.toString());
                assertTrue(available.draftChanges().isEmpty());
                assertTrue(available.tools().stream().anyMatch(receipt -> receipt.name().equals("search_services")
                    && receipt.status().equals("SUCCEEDED")));
                var detail = tools.call("get_service_details", catalogJson.valueToTree(Map.of("serviceId", selected.get("id"))), token);
                var captured = overviewData.path("services").valueStream()
                    .filter(entry -> entry.path("id").asLong() == ((Number) selected.get("id")).longValue()).findFirst().orElseThrow();
                assertEquals(catalogJson.valueToTree(detail.get("addOns")).valueStream()
                    .map(addOn -> addOn.path("id").asLong()).toList(),
                    captured.path("addOns").valueStream().map(addOn -> addOn.path("id").asLong()).toList());
                String requestName = "Vệ sinh nhà theo giờ".equals(serviceName) ? "vệ sinh nhà cửa" : serviceName;
                var choice = provider.reply(java.util.List.of(
                    Map.of("role", "SYSTEM", "content", Map.of("serviceId", 5, "packageId", 12, "quantity", 1)),
                    Map.of("role", "USER", "content", "Tôi muốn đặt " + requestName)), token);
                assertEquals(selected.get("id"), choice.draftChanges().get("serviceId"), choice.toString());
            }
            var shopping = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Có dịch vụ đi chợ hộ không")), token);
            assertTrue(shopping.text().contains("Đi chợ hộ"), shopping.toString());
            var shoppingAlias = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Có dịch vụ mua đồ ăn không?")), token);
            assertTrue(shoppingAlias.text().contains("Đi chợ hộ"), shoppingAlias.toString());
            var shoppingAddOns = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Đi chợ hộ có addon nào?")), token);
            var shoppingService = catalog.stream().filter(entry -> "GROCERY_SHOPPING".equals(entry.get("serviceType")))
                .findFirst().orElseThrow();
            var shoppingDetail = tools.call("get_service_details", catalogJson.valueToTree(Map.of("serviceId", shoppingService.get("id"))), token);
            for (var addOn : catalogJson.valueToTree(shoppingDetail).path("addOns")) {
                assertTrue(shoppingAddOns.text().contains(addOn.path("name").asText()), shoppingAddOns.toString());
            }
            var globalAddOn = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Có addon lau dọn tủ lạnh không?")), token);
            assertTrue(globalAddOn.text().contains("Lau dọn tủ lạnh"), globalAddOn.toString());
            assertTrue(globalAddOn.text().contains(shoppingService.get("name").toString()), globalAddOn.toString());
            var fullCatalog = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Cho tôi toàn bộ dịch vụ và addon của hệ thống")), token);
            for (var catalogEntry : overviewData.path("services")) {
                assertTrue(fullCatalog.text().contains(catalogEntry.path("name").asText()), fullCatalog.toString());
                for (var addOn : catalogEntry.path("addOns")) {
                    assertTrue(fullCatalog.text().contains(addOn.path("name").asText()), fullCatalog.toString());
                }
            }
            var unknown = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Có dịch vụ xây tàu vũ trụ không?")), token);
            assertTrue(unknown.text().contains("chưa tìm thấy"), unknown.toString());

            var staffing = provider.reply(java.util.List.of(
                Map.of("role", "SYSTEM", "content", Map.of("serviceId", 5, "quantity", 3, "packageId", 12, "mode", "B")),
                Map.of("role", "USER", "content", "Đặt giúp tôi vệ sinh 3 máy lạnh."),
                Map.of("role", "ASSISTANT", "content", "Bạn muốn 1 nhân viên làm lần lượt hay cần nhiều nhân viên để làm song song cho nhanh hơn?"),
                Map.of("role", "USER", "content", "Cho tôi hai người làm cùng lúc nhé.")), token);
            assertEquals(2, ((Number) staffing.draftChanges().getOrDefault("requiredStaffCount", 0)).intValue(), staffing.toString());

            var questionnaire = provider.reply(java.util.List.of(
                Map.of("role", "SYSTEM", "content", Map.of("serviceId", 6, "quantity", 1)),
                Map.of("role", "ASSISTANT", "content", "Bạn cho mình biết loại máy, vị trí dàn nóng, tình trạng và có kiểm tra gas không?"),
                Map.of("role", "USER", "content", "Loại máy lạnh treo tường, dàn nóng ở vị trí dễ tiếp cận, tình trạng cần vệ sinh định kì, có kiểm tra gas")),
                token);
            assertInstanceOf(Map.class, questionnaire.draftChanges().get("answers"), questionnaire.toString());
            var answers = (Map<?, ?>) questionnaire.draftChanges().get("answers");
            assertEquals("Treo tường", answers.get("airconType"));
            assertEquals("Dễ tiếp cận", answers.get("access"));
            assertEquals(true, answers.get("gasInspection"));
            assertTrue(answers.get("airconIssues") instanceof String text && !text.isBlank());

            var recovery = provider.reply(java.util.List.of(
                Map.of("role", "USER", "content", "Tôi muốn đặt vệ sinh 1 máy lạnh"),
                Map.of("role", "ASSISTANT", "content", "Mình đề xuất dịch vụ Bảo dưỡng & Nạp gas máy lạnh. Bạn cho mình biết loại máy, vị trí dàn nóng, tình trạng và có kiểm tra gas không?"),
                Map.of("role", "USER", "content", "Loại máy lạnh treo tường, dàn nóng ở vị trí dễ tiếp cận, tình trạng cần vệ sinh định kì, có kiểm tra gas"),
                Map.of("role", "ASSISTANT", "content", "Mình chưa lưu thay đổi. Câu trả lời chứa trường không thuộc dịch vụ."),
                Map.of("role", "USER", "content", "Loại máy lạnh treo tường, dàn nóng ở vị trí dễ tiếp cận, tình trạng cần vệ sinh định kì, có kiểm tra gas")),
                token);
            assertEquals(6, ((Number) recovery.draftChanges().getOrDefault("serviceId", 0)).intValue(), recovery.toString());
            assertEquals(answers, recovery.draftChanges().get("answers"));
        } finally {
            identity.stop(0);
        }
    }
}
