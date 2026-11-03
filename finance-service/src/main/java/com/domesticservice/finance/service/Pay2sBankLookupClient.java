package com.domesticservice.finance.service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class Pay2sBankLookupClient implements RecipientNameClient {
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;
    @Autowired
    public Pay2sBankLookupClient(@Value("${payments.bank-lookup.secret-key:}") String secret,ObjectMapper json) {
        this(secret,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),URI.create("https://api.pay2s.vn/userapi/get-recipient-name"));
    }
    Pay2sBankLookupClient(String secret,ObjectMapper json,HttpClient http,URI endpoint){this.secret=secret;this.json=json;this.http=http;this.endpoint=endpoint;}
    public boolean ready(){return secret!=null && !secret.isBlank();}
    public String provider(){return "PAY2S";}
    public Map<String,Object> lookup(String bin,String account) {
        if(!ready())throw unavailable("Chưa cấu hình khóa API tra tên người nhận Pay2S.");
        try {
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json")
                .header("pay2s-token",Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("bankCode",bin,"accountNumber",account)))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Dịch vụ tra tên đang giới hạn lượt gọi. Vui lòng thử lại sau một phút.");
            if(response.statusCode()!=200)throw unavailable("Chưa tra được tên người nhận; kiểm tra quyền API Pay2S hoặc thử lại sau.");
            var envelope=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            if(!Boolean.TRUE.equals(envelope.get("status")) || !(envelope.get("data") instanceof Map<?,?> data))
                throw unavailable("Pay2S chưa xác nhận được tài khoản ngân hàng này.");
            String name=Objects.toString(data.get("account_holder_name"),"").strip();
            if(!account.equals(data.get("account_number")) || !bin.equals(data.get("bank_code")) || name.isBlank() || name.length()>160)
                throw unavailable("Kết quả tra tên không khớp ngân hàng hoặc số tài khoản.");
            return Map.of("accountHolder",name,"proof",envelope);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable("Tra tên bị gián đoạn. Vui lòng thử lại.");}
        catch(java.io.IOException | IllegalArgumentException | tools.jackson.core.JacksonException ex){throw unavailable("Chưa nhận được kết quả tra tên hợp lệ.");}
    }
    private static ResponseStatusException unavailable(String message){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}
