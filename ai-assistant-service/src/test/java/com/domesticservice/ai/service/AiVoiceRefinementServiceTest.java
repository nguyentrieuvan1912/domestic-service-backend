package com.domesticservice.ai.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiVoiceRefinementServiceTest {
    @Test
    void acceptsCloseSoundingWordsAndPreservesFactsAndNegation() {
        assertEquals("Tui muốn giặt rèm ở nhà.", AiVoiceRefinementService.accept(
            "Tui muốn giặc rèm ở nhà.", "Tui muốn giặt rèm ở nhà.").text());
        assertEquals("Tôi cần nấu ăn.", AiVoiceRefinementService.accept("Tôi cần nâu ăn.", "Tôi cần nấu ăn.").text());
        for (String[] example : new String[][] {
            {"Tôi cần 2 người ngày 12/11.", "Tôi cần 3 người ngày 12/11."},
            {"Tôi không cần dọn nhà.", "Tôi cũng cần dọn nhà."},
            {"Chưa cần đặt lịch.", "Chùa cần đặt lịch."},
            {"Nhà có năm người.", "Nhà có nấm người."},
            {"Tôi muốn nấu ăn.", "Tôi muốn lau nhà."},
            {"Đi chợ.", "Tôi muốn đi chợ hộ chiều nay."},
            {"Tôi muốn đặt dịch vụ.", ""}
        }) {
            var result = AiVoiceRefinementService.accept(example[0], example[1]);
            assertFalse(result.corrected());
            assertEquals(example[0], result.text());
        }
    }

    @Test
    void usesStructuredModelOutputAndFallsBackOnInvalidResponses() throws Exception {
        var content = new AtomicReference<>("{\"text\":\"Tui muốn giặt rèm ở nhà.\"}");
        var request = new AtomicReference<String>();
        var json = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.writeValueAsBytes(Map.of("done", true, "done_reason", "stop",
                "message", Map.of("content", content.get())));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var tools = mock(AiBusinessTools.class);
            when(tools.directory()).thenReturn(List.of(Map.of("id", 7L, "name", "Giặt rèm")));
            var service = new AiVoiceRefinementService("http://127.0.0.1:" + server.getAddress().getPort(),
                "qwen3:8b", tools, json);
            String original = "Tui muốn giặc rèm ở nhà.";
            var result = service.refine(original);
            assertTrue(result.corrected());
            assertEquals(original, result.originalText());
            assertEquals("Tui muốn giặt rèm ở nhà.", result.text());
            var payload = json.readTree(request.get());
            assertFalse(payload.path("think").asBoolean());
            assertTrue(payload.path("format").isObject());
            assertTrue(payload.path("messages").get(1).path("content").asText().contains("Giặt rèm"));
            content.set("invalid model output");
            assertEquals(original, service.refine(original).text());
            when(tools.directory()).thenThrow(new IllegalStateException("catalog unavailable"));
            assertFalse(service.refine(original).corrected());
        } finally {
            server.stop(0);
        }
    }
}
