package com.domesticservice.finance.service;

import com.domesticservice.finance.config.SepayProperties;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SepayClient {
    private final SepayProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI base;
    @org.springframework.beans.factory.annotation.Autowired
    public SepayClient(SepayProperties config,ObjectMapper json){this(config,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("https://userapi.sepay.vn/v2"));}
    SepayClient(SepayProperties config,ObjectMapper json,HttpClient http,URI base){this.config=config;this.json=json;this.http=http;this.base=base;}
    public void requireReady(){if(!config.ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình đủ TPBank/SePay. Điền cấu hình backend trước khi chi.");}
    @SuppressWarnings("unchecked")
    private Map<String,Object> get(String path) {
        requireReady();
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(base.toString()+path))
                .header("Authorization","Bearer "+config.apiToken()).header("Accept","application/json")
                .timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofString());
            var body=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            if(response.statusCode()!=200 || !"success".equals(body.get("status")))throw unavailable();
            return body;
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable();}
        catch(java.io.IOException | tools.jackson.core.JacksonException | IllegalArgumentException ex){throw unavailable();}
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> source() {
        var response=get("/bank-accounts/"+config.bankAccountId());
        if(!(response.get("data") instanceof Map<?,?>))throw unavailable();
        var bank=(Map<String,Object>)response.get("data");
        if(!Objects.equals(bank.get("id"),config.bankAccountId()) || !Objects.equals(bank.get("account_number"),config.sourceAccount())
            || !PayosPayoutClient.normalizeName(bank.get("account_holder_name")).equals(PayosPayoutClient.normalizeName(config.sourceName()))
            || !"TPBANK".equalsIgnoreCase(Objects.toString(bank.getOrDefault("bank_short_name",bank.get("bank_brand_name")),""))
            || !"1".equals(Objects.toString(bank.get("active"),"")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Nguồn chi SePay không khớp tài khoản TPBank chính chủ đã cấu hình hoặc bị ngừng.");
        return bank;
    }
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> candidates(String reference,String bankId) {
        var body=get("/transactions?bank_account_id="+URLEncoder.encode(bankId,StandardCharsets.UTF_8)
            +"&transfer_type=out&q="+URLEncoder.encode(reference,StandardCharsets.UTF_8)+"&per_page=100&page=1");
        if(!(body.get("data") instanceof List<?> rows))throw unavailable();
        // More than one page is ambiguous, not a license to settle a partial search result.
        if(body.get("meta") instanceof Map<?,?> meta && meta.get("pagination") instanceof Map<?,?> page && Boolean.TRUE.equals(page.get("has_more")))throw unavailable();
        return rows.stream().filter(Map.class::isInstance).map(row->(Map<String,Object>)row).toList();
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> transaction(String id) {
        if(!id.matches("[0-9a-fA-F-]{36}"))throw unavailable();
        var body=get("/transactions/"+id);
        if(!(body.get("data") instanceof Map<?,?>))throw unavailable();
        var detail=(Map<String,Object>)body.get("data");
        if(!id.equals(detail.get("id")))throw unavailable();
        return detail;
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa xác minh được dữ liệu SePay. Giữ khoản rút để đối soát; không chuyển lại.");}
}
