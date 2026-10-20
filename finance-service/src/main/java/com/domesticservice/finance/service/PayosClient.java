package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayosProperties;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(PayosProperties.class)
public class PayosClient {
    private final PayosProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI base;

    @org.springframework.beans.factory.annotation.Autowired
    public PayosClient(PayosProperties config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(), URI.create("https://api-merchant.payos.vn"));
    }
    // Package-private transport injection: tests never call the real provider.
    PayosClient(PayosProperties config, ObjectMapper json, HttpClient http, URI base) {
        this.config = config; this.json = json; this.http = http; this.base = base;
    }
    public boolean ready() { return config.ready(); }
    public void requireReady() {
        if (!ready()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa kết nối payOS. Cần cấu hình kênh thanh toán và tài khoản nhận tiền trước khi nạp.");
    }
    public String expectedAccount() { return config.accountNumber(); }

    public Map<String, Object> create(long id, long amount, String description, long expiresAt) {
        var body = new LinkedHashMap<String, Object>();
        body.put("amount", amount); body.put("cancelUrl", config.cancelUrl());
        body.put("description", description); body.put("orderCode", id); body.put("returnUrl", config.returnUrl());
        body.put("signature", sign(body)); body.put("expiredAt", expiresAt);
        return call("POST", "/v2/payment-requests", body, false);
    }
    public Map<String, Object> lookup(long id) { return call("GET", "/v2/payment-requests/" + id, null, true); }

    public String sign(Map<String, Object> data) {
        try {
            String canonical = new TreeMap<>(data).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + signatureValue(entry.getValue())).collect(Collectors.joining("&"));
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.checksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("Không tạo được chữ ký.", error); }
    }
    private String signatureValue(Object value) {
        if (value == null || "null".equals(value) || "undefined".equals(value)) return "";
        if (value instanceof List<?> list) return json.writeValueAsString(list.stream().map(item -> item instanceof Map<?, ?> map ? new TreeMap<>(map) : item).toList());
        return value.toString();
    }
    public boolean verify(Map<String, Object> data, String signature) {
        if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(sign(data)), HexFormat.of().parseHex(signature));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body, boolean allowMissing) {
        requireReady();
        try {
            var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("x-client-id", config.clientId()).header("x-api-key", config.apiKey()).header("Content-Type", "application/json");
            if (body == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && allowMissing) return null;
            if (response.statusCode() != 200) throw unavailable();
            var envelope = json.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            if (allowMissing && "231".equals(envelope.get("code"))) return null;
            if (!"00".equals(envelope.get("code")) || !(envelope.get("data") instanceof Map<?, ?>)) throw unavailable();
            var data = (Map<String, Object>) envelope.get("data");
            if (!verify(data, Objects.toString(envelope.get("signature"), ""))) throw unavailable();
            return data;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException | IllegalArgumentException | tools.jackson.core.JacksonException error) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa xác minh được giao dịch với payOS. Không chuyển thêm tiền; hãy kiểm tra lại yêu cầu hiện tại.");
    }
}
