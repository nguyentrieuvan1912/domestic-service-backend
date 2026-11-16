package com.domesticservice.ai.service;

import com.domesticservice.ai.repository.AiConversationSqlRepository;
import com.domesticservice.ai.repository.BookingDraftRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real Ollama and Catalog, with PostgreSQL and authentication isolated from customer data. */
@EnabledIfEnvironmentVariable(named = "AI_BENCHMARK_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "ai.knowledge.indexer.enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5551/postgres",
    "spring.datasource.username=postgres", "spring.datasource.password=ai_verify_only"
})
class AiConversationBenchmarkTests {
    @Autowired AiConversationSqlRepository conversations;
    @Autowired BookingDraftRepository draftRepository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AiCustomerKnowledge knowledge;
    @Autowired ObjectMapper json;
    @Autowired CatalogSemanticRecommendations recommendations;
    private HttpServer identity;
    private AiConversationService service;
    private AiBookingDraftService drafts;
    private final List<Map<String, Object>> results = new ArrayList<>();
    private static final String TOKEN = "Bearer isolated-benchmark";
    private int expectedFields;
    private int matchingFields;

    @BeforeAll
    void connectRealServices() throws Exception {
        identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/api/v1/identity/auth/me", exchange -> {
            byte[] body = "{\"id\":11,\"role\":\"CUSTOMER\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        identity.createContext("/api/v1/identity/customer/addresses", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        identity.createContext("/api/v1/booking/bookings", exchange -> {
            String path = exchange.getRequestURI().getPath();
            boolean authorized = TOKEN.equals(exchange.getRequestHeaders().getFirst("Authorization"));
            String newest = """
                {"id":752,"bookingCode":"CM-752","status":"MATCHING","paymentStatus":"PAID",
                 "service":{"name":"Vệ sinh máy lạnh"}}
                """;
            String older = """
                {"id":751,"bookingCode":"CM-751","status":"COMPLETED","paymentStatus":"PAID",
                 "service":{"name":"Đi chợ hộ"}}
                """;
            String body = switch (path) {
                case "/api/v1/booking/bookings" -> "[" + newest + "," + older + "]";
                case "/api/v1/booking/bookings/751" -> older;
                default -> "";
            };
            byte[] bytes = (authorized && !body.isEmpty() ? body : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(!authorized ? 403 : body.isEmpty() ? 404 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        identity.start();
        String identityUrl = "http://127.0.0.1:" + identity.getAddress().getPort();
        String catalogUrl = "http://127.0.0.1:8082";
        var tools = new AiBusinessTools(catalogUrl, identityUrl, identityUrl, json, recommendations);
        var provider = new OllamaChatProvider("http://127.0.0.1:11434",
            System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3:8b"), tools, json, knowledge);
        drafts = new AiBookingDraftService(draftRepository, conversations, transactions, json, identityUrl, catalogUrl);
        service = new AiConversationService(conversations, provider, transactions, drafts, identityUrl);
    }

    @TestFactory
    Stream<DynamicTest> naturalOrderQueriesUseBookingApi() {
        var cases = Map.of(
            "Lần thuê gần đây nhất của tôi có người nhận việc chưa?", "CM-752",
            "Cho mình xem tình trạng thanh toán của mã đơn CM-751", "CM-751",
            "Đơn số 751 của tôi đã hoàn thành chưa?", "CM-751");
        return cases.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            long conversation = ((Number) service.create(TOKEN).get("id")).longValue();
            var reply = service.send(TOKEN, conversation, Map.of("content", entry.getKey(),
                "clientKey", UUID.randomUUID().toString()));
            String text = service.messages(TOKEN, conversation).getLast().get("content").toString();
            assertTrue(text.contains(entry.getValue()), text);
            assertTrue(text.contains("Đã thanh toán"), text);
            assertFalse(text.contains(entry.getValue().equals("CM-752") ? "CM-751" : "CM-752"), text);
            assertFalse(reply.containsKey("bookingDraft"));
        }));
    }

    @TestFactory
    Stream<DynamicTest> unseenRequestsAndQuestions() {
        var cases = Map.ofEntries(
            Map.entry("Tìm bảo mẫu trông bé 2 tuổi bán thời gian", "CHILD_CARE"),
            Map.entry("Vợ chồng tôi đều đi ca, ai ở với nhóc nhà tôi một buổi được nhỉ", "CHILD_CARE"),
            Map.entry("Tuần này tôi nằm viện, ghé lựa ít rau quả mang sang nhà dùm với", "GROCERY_SHOPPING"),
            Map.entry("Mấy bộ đồ mặc đi làm nhăn hết rồi, nhờ ai mang về làm phẳng cho mình", "LAUNDRY"),
            Map.entry("Bà ở một mình cả ngày, sắp xếp người qua hỗ trợ bà sinh hoạt với", "ELDERLY_CARE"),
            Map.entry("Nhà đi du lịch vài hôm, qua đổ hạt và thay nước cho cún nhà em với", "PET_CARE"),
            Map.entry("Co ai ghe lam sach cai ghe phong khach bi tre lam do nuoc cam khong nhi", "CLEANING_UPHOLSTERY"),
            Map.entry("Mình đang so sánh thôi, thuê người lo cho bé tốn khoảng bao nhiêu?", ""),
            Map.entry("Nếu lịch đã đặt mà hôm đó mình có việc đột xuất thì xử lý thế nào?", ""),
            Map.entry("Kiếm người thay màn hình laptop bị nứt giúp tôi", ""));
        return cases.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            long conversation = ((Number) service.create(TOKEN).get("id")).longValue();
            var reply = service.send(TOKEN, conversation, Map.of("content", entry.getKey(),
                "clientKey", UUID.randomUUID().toString()));
            var persisted = json.valueToTree(drafts.get(TOKEN, conversation));
            if (entry.getValue().isEmpty()) {
                assertTrue(persisted.path("id").isNull());
                assertFalse(reply.containsKey("bookingDraft"));
                return;
            }
            assertEquals(entry.getValue(), persisted.path("choices").path("service").path("serviceType").asText(),
                () -> service.messages(TOKEN, conversation).getLast().get("content").toString());
            assertTrue(reply.containsKey("bookingDraft"));
            if (entry.getKey().contains("2 tuổi")) {
                assertEquals("1–3 tuổi", persisted.path("data").path("answers").path("childAge").asText());
                long packageId = persisted.path("data").path("packageId").asLong();
                assertTrue(persisted.path("choices").path("packages").valueStream().anyMatch(pack ->
                    pack.path("id").asLong() == packageId && pack.path("name").asText().contains("bán thời gian")));
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> naturalBookingRequestsUseLiveCatalog() {
        var requests = Map.ofEntries(
            Map.entry("Tôi muốn mua thực phẩm và giao tới cho tôi", "Đi chợ hộ & Chọn thực phẩm tươi"),
            Map.entry("Toi can nguoi trong be", "Bảo mẫu & Chăm sóc trẻ em"),
            Map.entry("Tôi cần người chăm ông bà", "Chăm sóc người cao tuổi"),
            Map.entry("Mình muốn tắm cho mèo", "Chăm sóc thú cưng tại nhà"),
            Map.entry("Nhờ tưới cây ban công", "Chăm sóc cây cảnh ban công & sân vườn"),
            Map.entry("Giặt quần áo giúp tôi", "Giặt ủi & Là phẳng quần áo"),
            Map.entry("Tôi muốn thuê người nấu cơm", "Nấu ăn gia đình chuẩn vị"),
            Map.entry("Nạp gas điều hòa giúp tôi", "Bảo dưỡng & Nạp gas máy lạnh"),
            Map.entry("Mình muốn rửa điều hòa", "Vệ sinh máy lạnh"),
            Map.entry("Mình cần làm sạch lồng giặt", "Vệ sinh máy giặt lồng đứng & lồng ngang"),
            Map.entry("Tôi muốn làm sạch máy sấy", "Vệ sinh máy sấy quần áo"),
            Map.entry("Tôi cần người khử mùi tủ lạnh", "Vệ sinh & Khử mùi tủ lạnh"),
            Map.entry("Mình muốn giặt ghế sofa", "Vệ sinh sofa, nệm & rèm"),
            Map.entry("Tôi cần phun khử trùng", "Phun khử khuẩn & Khử mùi ẩm mốc"),
            Map.entry("Tôi cần người dọn nhà theo ca", "Dọn dẹp theo ca"),
            Map.entry("Tôi muốn dọn nhà hàng tuần", "Dọn dẹp định kỳ"),
            Map.entry("Tôi muốn thuê người giúp việc theo giờ", "Dọn dẹp nhà theo giờ"),
            Map.entry("Tôi cần vệ sinh nhà cửa", "Vệ sinh nhà theo giờ"),
            Map.entry("Mình cần dọn nhà sau sửa", "Tổng vệ sinh"),
            Map.entry("Tôi cần tổng vệ sinh toàn diện căn hộ", "Tổng vệ sinh toàn diện"));
        return requests.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            long conversation = ((Number) service.create(TOKEN).get("id")).longValue();
            var reply = service.send(TOKEN, conversation, Map.of("content", entry.getKey(),
                "clientKey", UUID.randomUUID().toString()));
            var persisted = json.readTree(json.writeValueAsString(drafts.get(TOKEN, conversation)));
            assertEquals(entry.getValue(), persisted.path("choices").path("service").path("name").asText());
            assertEquals(persisted, json.readTree(json.writeValueAsString(reply.get("bookingDraft"))));
            if ("GROCERY_SHOPPING".equals(persisted.path("choices").path("service").path("serviceType").asText())) {
                var data = persisted.path("data");
                assertTrue(data.path("packageId").asLong() > 0);
                assertEquals(1, data.path("quantity").asInt());
                assertFalse(data.has("addressId"));
                assertFalse(data.has("answers"));
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> vietnameseConversations() throws Exception {
        try (var input = getClass().getResourceAsStream("/ai/vietnamese-conversations.json")) {
            assertNotNull(input);
            var cases = json.readTree(input);
            assertEquals(60, cases.size(), "Keep the declared benchmark size reproducible.");
            return cases.valueStream().map(testCase -> DynamicTest.dynamicTest(testCase.path("id").asText(),
                () -> evaluate(testCase))).toList().stream();
        }
    }

    private void evaluate(JsonNode testCase) {
        long started = System.nanoTime();
        var result = new LinkedHashMap<String, Object>();
        result.put("id", testCase.path("id").asText());
        result.put("group", testCase.path("group").asText());
        expectedFields += testCase.path("fields").size();
        var errors = new ArrayList<String>();
        try {
            long conversation = ((Number) service.create(TOKEN).get("id")).longValue();
            for (var message : testCase.path("messages")) {
                service.send(TOKEN, conversation, Map.of("content", message.asText(), "clientKey", UUID.randomUUID().toString()));
            }
            var stored = service.messages(TOKEN, conversation);
            assertEquals(testCase.path("messages").size() * 2, stored.size(), "Every turn must be saved exactly once.");
            String answer = stored.getLast().get("content").toString();
            var draft = json.valueToTree(drafts.get(TOKEN, conversation));
            var data = draft.path("data");
            result.put("answer", answer);
            result.put("draft", data);
            for (var expected : testCase.path("contains")) {
                if (!answer.contains(expected.asText())) errors.add("Missing answer text: " + expected.asText());
            }
            for (var forbidden : testCase.path("forbidden")) {
                if (answer.toLowerCase(java.util.Locale.ROOT).contains(forbidden.asText().toLowerCase(java.util.Locale.ROOT))) {
                    errors.add("Unsupported claim: " + forbidden.asText());
                }
            }
            if (testCase.path("noDraft").asBoolean() && !draft.path("id").isNull()) errors.add("Consultation created a draft.");
            var selectedService = draft.path("choices").path("service");
            for (String key : List.of("serviceName", "serviceType")) {
                if (!testCase.has(key)) continue;
                String field = key.equals("serviceName") ? "name" : "serviceType";
                if (!testCase.path(key).asText().equals(selectedService.path(field).asText())) {
                    errors.add("Wrong " + key + ": " + selectedService.path(field).asText());
                }
            }
            for (var entry : testCase.path("fields").properties()) {
                var actual = field(data, entry.getKey());
                boolean matches = actual.isNumber() && entry.getValue().isNumber()
                    ? actual.decimalValue().compareTo(entry.getValue().decimalValue()) == 0 : actual.equals(entry.getValue());
                if (matches) matchingFields++;
                else errors.add("Wrong field " + entry.getKey() + ": expected " + entry.getValue() + ", got " + actual);
            }
            for (var absent : testCase.path("absent")) {
                if (!field(data, absent.asText()).isMissingNode()) errors.add("Unexpected field: " + absent.asText());
            }
            for (var required : testCase.path("nonEmpty")) {
                if (field(data, required.asText()).asText().isBlank()) errors.add("Empty field: " + required.asText());
            }
        } catch (Exception failure) {
            errors.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } catch (AssertionError failure) {
            errors.add(failure.getMessage());
        } finally {
            result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            result.put("passed", errors.isEmpty());
            result.put("errors", errors);
            results.add(result);
            System.out.println("AI benchmark " + result.get("id") + ": " + (errors.isEmpty() ? "PASS" : errors)
                + " (" + result.get("elapsedMs") + " ms)");
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    private static JsonNode field(JsonNode data, String name) {
        JsonNode value = data;
        for (String part : name.split("\\.")) value = value.path(part);
        return value;
    }

    @AfterAll
    void writeReport() throws Exception {
        if (identity != null) identity.stop(0);
        var report = new LinkedHashMap<String, Object>();
        report.put("generatedAt", java.time.Instant.now().toString());
        report.put("model", System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3:8b"));
        report.put("scope", "60 authored synthetic conversations; real Catalog and Ollama; isolated PostgreSQL and identity fixture. Includes direct catalog routes and LLM extraction.");
        report.put("cases", results.size());
        report.put("passed", results.stream().filter(result -> Boolean.TRUE.equals(result.get("passed"))).count());
        report.put("expectedFieldChecks", expectedFields);
        report.put("matchingFieldChecks", matchingFields);
        var times = results.stream().map(result -> ((Number) result.get("elapsedMs")).longValue()).sorted().toList();
        if (!times.isEmpty()) {
            report.put("medianConversationMs", times.get((times.size() - 1) / 2));
            report.put("p95ConversationMs", times.get(Math.min(times.size() - 1, (int) Math.ceil(times.size() * 0.95) - 1)));
        }
        var groups = new LinkedHashMap<String, Object>();
        results.stream().map(result -> result.get("group").toString()).distinct().sorted().forEach(group -> {
            var cases = results.stream().filter(result -> group.equals(result.get("group"))).toList();
            groups.put(group, Map.of("cases", cases.size(), "passed", cases.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("passed"))).count()));
        });
        report.put("groups", groups);
        report.put("results", results);
        Path output = Path.of("target", "ai-benchmark", "results.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
