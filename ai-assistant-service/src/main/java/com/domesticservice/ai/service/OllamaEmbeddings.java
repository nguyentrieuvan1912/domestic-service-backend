package com.domesticservice.ai.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
class OllamaEmbeddings {
    private final RestClient client;
    final String model;

    OllamaEmbeddings(@Value("${OLLAMA_BASE_URL:http://127.0.0.1:11434}") String baseUrl,
                     @Value("${OLLAMA_EMBEDDING_MODEL:qwen3-embedding:0.6b}") String model) {
        this.model = model.strip();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        client = RestClient.builder().baseUrl(baseUrl.strip()).requestFactory(factory).build();
    }

    List<List<Double>> documents(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            var response = client.post().uri("/api/embed")
                .body(Map.of("model", model, "input", texts, "truncate", false, "keep_alive", "15m",
                    "options", Map.of("num_ctx", 2048)))
                .retrieve().body(JsonNode.class);
            if (response == null || response.path("embeddings").size() != texts.size()) throw unavailable();
            var vectors = new ArrayList<List<Double>>();
            int dimension = 0;
            for (var row : response.path("embeddings")) {
                if (!row.isArray() || row.size() < 8 || row.size() > 8192) throw unavailable();
                if (dimension != 0 && row.size() != dimension) throw unavailable();
                dimension = row.size();
                var vector = new ArrayList<Double>();
                double norm = 0;
                for (var item : row) {
                    if (!item.isNumber() || !Double.isFinite(item.asDouble())) throw unavailable();
                    vector.add(item.asDouble());
                    norm += item.asDouble() * item.asDouble();
                }
                if (norm <= 0) throw unavailable();
                double magnitude = Math.sqrt(norm);
                vectors.add(vector.stream().map(value -> value / magnitude).toList());
            }
            return vectors;
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    List<Double> query(String question) {
        return documents(List.of("Instruct: Retrieve official CleanMaster customer guidance relevant to the question.\nQuery: " + question)).getFirst();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa tạo được embedding local. Kiểm tra Ollama và model embedding rồi thử lập chỉ mục lại.");
    }
}
