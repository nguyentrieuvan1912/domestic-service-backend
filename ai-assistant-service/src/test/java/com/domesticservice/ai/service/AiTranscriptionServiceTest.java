package com.domesticservice.ai.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AiTranscriptionServiceTest {
    @Test
    void forwardsAudioWithFixedNameAndVietnameseAndReturnsOnlyTheTranscript() throws Exception {
        var request = new AtomicReference<String>();
        var transcript = new AtomicReference<>("""
            {"text":"ignored","segments":[
              {"text":"  Vệ sinh máy lạnh  ","start":0,"end":1.5,"avg_logprob":-0.1,"no_speech_prob":0.01},
              {"text":"Vệ sinh máy lạnh.","start":1.5,"end":1.55,"avg_logprob":-0.1,"no_speech_prob":0.01}
            ]}
            """);
        var corrupt = new java.util.concurrent.atomic.AtomicBoolean();
        var whisper = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        whisper.createContext("/inference", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean conversionFailed = corrupt.get();
            byte[] response = (conversionFailed ? "{\"error\":\"FFmpeg conversion failed.\"}"
                : transcript.get()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(conversionFailed ? 500 : 200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        whisper.start();
        try {
            String url = "http://127.0.0.1:" + whisper.getAddress().getPort();
            var service = new AiTranscriptionService(url, false);
            var audio = new MockMultipartFile("audio", "untrusted-name", "audio/wav",
                "RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII));
            assertEquals("Vệ sinh máy lạnh", service.transcribe(audio));
            assertTrue(request.get().contains("filename=\"recording.wav\""));
            assertTrue(request.get().contains("name=\"language\""));
            assertTrue(request.get().contains("\r\n\r\nvi\r\n"));
            assertTrue(request.get().contains("\r\n\r\nverbose_json\r\n"));
            assertFalse(request.get().contains("untrusted-name"));
            assertFalse(request.get().contains("name=\"audio_ctx\""));

            var shortRecording = new MockMultipartFile("audio", "short.wav", "audio/wav", pcmWav(3));
            assertEquals("Vệ sinh máy lạnh", service.transcribe(shortRecording));
            assertTrue(request.get().contains("name=\"audio_ctx\""));
            assertTrue(request.get().contains("\r\n\r\n400\r\n"));
            assertEquals("Vệ sinh máy lạnh", new AiTranscriptionService(url, true).transcribe(shortRecording));
            assertFalse(request.get().contains("name=\"audio_ctx\""));

            var fullRecording = new MockMultipartFile("audio", "full.wav", "audio/wav", pcmWav(30));
            assertEquals("Vệ sinh máy lạnh", service.transcribe(fullRecording));
            assertFalse(request.get().contains("name=\"audio_ctx\""));

            transcript.set("""
                {"segments":[{"text":"Một đoạn nhận dạng kéo dài quá bản thu.","start":0,"end":12,"avg_logprob":-0.1,"no_speech_prob":0.01}]}
                """);
            assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> new AiTranscriptionService(url, true).transcribe(shortRecording)).getStatusCode().value());
            transcript.set("""
                {"segments":[{"text":"Đi chợ.","start":0,"end":3.1,"avg_logprob":-0.1,"no_speech_prob":0.01}]}
                """);
            assertEquals("Đi chợ.", service.transcribe(shortRecording));

            transcript.set("""
                {"segments":[{"text":"Để không bỏ lỡ những video hấp dẫn.","start":0,"end":2,"avg_logprob":-0.1,"no_speech_prob":0.45}]}
                """);
            assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.transcribe(shortRecording)).getStatusCode().value());
            transcript.set("""
                {"duration":1,"segments":[{"text":"Nội dung vượt thời lượng.","start":0,"end":3,"avg_logprob":-0.1,"no_speech_prob":0.01}]}
                """);
            assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.transcribe(audio)).getStatusCode().value());

            for (String[] example : new String[][] {
                {"Đi chờ.", "Đi chợ."},
                {"Tôi muốn đi chờ nhé.", "Tôi muốn đi chợ nhé."},
                {"Đặt dịch vụ đi chờ hộ chiều nay.", "Đặt dịch vụ đi chợ hộ chiều nay."},
                {"Tôi đi chờ bạn ở sảnh.", "Tôi đi chờ bạn ở sảnh."}
            }) {
                transcript.set("""
                    {"segments":[{"text":"%s","start":0,"end":2,"avg_logprob":-0.1,"no_speech_prob":0.01}]}
                    """.formatted(example[0]));
                assertEquals(example[1], service.transcribe(audio));
            }

            transcript.set("""
                {"segments":[{"text":"Câu không chắc chắn","start":0,"end":2,"avg_logprob":-1.5,"no_speech_prob":0.7}]}
                """);
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.transcribe(audio)).getStatusCode().value());
            transcript.set("{\"segments\":[]}");
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.transcribe(audio)).getStatusCode().value());

            corrupt.set(true);
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.transcribe(audio)).getStatusCode().value());

            request.set(null);
            var invalid = new MockMultipartFile("audio", "invalid.wav", "audio/wav", new byte[] {1, 2});
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.transcribe(invalid)).getStatusCode().value());
            var large = new MockMultipartFile("audio", "large.wav", "audio/wav", new byte[2 * 1024 * 1024 + 1]);
            assertEquals(413, assertThrows(ResponseStatusException.class, () -> service.transcribe(large)).getStatusCode().value());
            assertNull(request.get());
        } finally {
            whisper.stop(0);
        }
    }

    private static byte[] pcmWav(int seconds) {
        int rate = 16000;
        int size = seconds * rate * 2;
        var wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + size);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        wav.putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2);
        wav.putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size);
        return wav.array();
    }
}
