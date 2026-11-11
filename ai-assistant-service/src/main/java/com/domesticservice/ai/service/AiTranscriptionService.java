package com.domesticservice.ai.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class AiTranscriptionService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern GROCERY_SERVICE = Pattern.compile(
        "(?iu)(?<![\\p{L}\\p{M}])(đi\\s+)chờ(?=\\s+hộ(?![\\p{L}\\p{M}]))");
    private static final Pattern SHORT_GROCERY_REQUEST = Pattern.compile(
        "(?iu)^((?:(?:tôi|mình|em|anh|chị)\\s+)?(?:(?:muốn|cần)\\s+)?đi\\s+)chờ((?:\\s+(?:ạ|nhé))?[.!?…]*)$");
    private final RestClient whisper;
    private final boolean fullContext;
    private final Semaphore available = new Semaphore(1);

    public AiTranscriptionService(@Value("${AI_TRANSCRIPTION_URL:}") String baseUrl,
                                  @Value("${AI_TRANSCRIPTION_FULL_CONTEXT:false}") boolean fullContext) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        factory.setReadTimeout(Duration.ofSeconds(55));
        whisper = baseUrl.isBlank() ? null : RestClient.builder().baseUrl(baseUrl.strip()).requestFactory(factory).build();
        this.fullContext = fullContext;
    }

    public String transcribe(MultipartFile audio) {
        if (audio.isEmpty()) throw bad("Bản thu âm đang trống.");
        if (audio.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Bản thu âm vượt quá 2 MB.");
        }
        if (whisper == null) throw unavailable();
        if (!available.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đang xử lý một bản thu âm khác. Bạn thử lại sau nhé.");
        }
        try {
            byte[] data = audio.getBytes();
            String extension = extension(data);
            var multipart = new LinkedMultiValueMap<String, Object>();
            multipart.add("file", new ByteArrayResource(data) {
                @Override public String getFilename() { return "recording." + extension; }
            });
            multipart.add("language", "vi");
            multipart.add("response_format", "verbose_json");
            multipart.add("token_timestamps", "false");
            multipart.add("vad_threshold", "0.65");
            multipart.add("no_speech_thold", "0.35");
            double audioDuration = pcmWavDuration(data, extension);
            int context = fullContext ? 0 : shortWavContext(audioDuration);
            if (context > 0) multipart.add("audio_ctx", Integer.toString(context));
            var result = whisper.post().uri("/inference").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart).retrieve().body(JsonNode.class);
            return verifiedTranscript(result, audioDuration);
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 400 || (failure.getStatusCode().value() == 500
                && failure.getResponseBodyAsString().contains("FFmpeg conversion failed."))) {
                throw bad("Không đọc được bản thu âm. Bạn thử thu lại nhé.");
            }
            throw unavailable();
        } catch (IOException | RestClientException failure) {
            throw unavailable();
        } finally {
            available.release();
        }
    }

    private static String verifiedTranscript(JsonNode result, double audioDuration) {
        if (result == null || !result.path("segments").isArray()) throw unavailable();
        if (!Double.isFinite(audioDuration)) audioDuration = result.path("duration").asDouble(Double.NaN);
        var transcript = new StringJoiner(" ");
        String previous = "";
        for (JsonNode segment : result.path("segments")) {
            String text = segment.path("text").asText("").strip();
            if (text.isEmpty() || text.matches("(?s)\\[[^\\]]+]")) continue;

            String words = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").strip();
            double start = segment.path("start").asDouble(Double.NaN);
            double end = segment.path("end").asDouble(Double.NaN);
            double duration = end - start;
            int wordCount = words.isEmpty() ? 0 : words.split(" ").length;
            boolean tooManyWords = wordCount > Math.max(3, duration * 14 + 2);
            // A duplicate emitted in an implausibly short timestamp is a decoder artifact.
            if (tooManyWords && words.equals(previous)) continue;
            if (!Double.isFinite(duration) || duration <= 0 || start < 0 || tooManyWords
                || (Double.isFinite(audioDuration) && end > audioDuration + 0.25)
                || segment.path("avg_logprob").asDouble(Double.NEGATIVE_INFINITY) < -0.8
                || segment.path("no_speech_prob").asDouble(1) >= 0.35) {
                throw unclearSpeech();
            }
            transcript.add(text);
            previous = words;
        }
        if (transcript.length() == 0) throw unclearSpeech();
        // Fix this common grocery-service homophone while preserving ordinary uses of “chờ”.
        String text = GROCERY_SERVICE.matcher(transcript.toString()).replaceAll("$1chợ");
        return SHORT_GROCERY_REQUEST.matcher(text).replaceFirst("$1chợ$2");
    }

    private static ResponseStatusException unclearSpeech() {
        return bad("Chưa nghe rõ lời nói. Bạn thử thu âm lại nhé.");
    }

    private static String extension(byte[] data) {
        if (data.length >= 12) {
            if (ascii(data, 4, "ftyp")) return "m4a";
            if (ascii(data, 0, "RIFF") && ascii(data, 8, "WAVE")) return "wav";
            if (ascii(data, 0, "OggS")) return "ogg";
            if (data[0] == 0x1a && data[1] == 0x45 && data[2] == (byte) 0xdf && data[3] == (byte) 0xa3) return "webm";
        }
        throw bad("Định dạng bản thu âm không được hỗ trợ.");
    }

    private static double pcmWavDuration(byte[] data, String extension) {
        // The app generates a single, canonical PCM data chunk. Let Whisper handle other formats normally.
        if (!"wav".equals(extension) || data.length <= 44 || !ascii(data, 12, "fmt ") || !ascii(data, 36, "data")) return Double.NaN;
        var header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int rate = header.getInt(24);
        int pcmBytes = header.getInt(40);
        if (header.getInt(16) != 16 || header.getShort(20) != 1 || header.getShort(22) != 1
            || header.getShort(34) != 16 || rate < 8000 || rate > 48000 || pcmBytes != data.length - 44 || pcmBytes % 2 != 0) {
            return Double.NaN;
        }
        return pcmBytes / (rate * 2.0);
    }

    private static int shortWavContext(double duration) {
        if (!Double.isFinite(duration)) return 0;
        // Whisper uses 50 context positions per second. Round up to two-second blocks and leave four seconds of margin.
        int context = Math.max(400, ((int) Math.ceil(duration / 2) + 2) * 100);
        return context < 1500 ? context : 0;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        return text.equals(new String(data, offset, text.length(), StandardCharsets.US_ASCII));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Chuyển giọng nói thành chữ chưa sẵn sàng. Bạn vẫn có thể nhập tin nhắn.");
    }
}
