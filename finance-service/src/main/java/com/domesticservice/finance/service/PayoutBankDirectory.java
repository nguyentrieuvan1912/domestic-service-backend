package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayoutProperties;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PayoutBankDirectory {
    private final PayoutProperties config;
    private final ObjectMapper json;
    private List<Map<String,Object>> cached=List.of();
    private Instant expires=Instant.EPOCH;
    public PayoutBankDirectory(PayoutProperties config,ObjectMapper json){this.config=config;this.json=json;}
    public synchronized List<Map<String,Object>> banks() {
        if(config.allowedBins().isEmpty()) return List.of();
        if(Instant.now().isBefore(expires)) return cached;
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.vietqr.io/v2/banks")).timeout(Duration.ofSeconds(8)).GET().build();
            var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build().send(request,HttpResponse.BodyHandlers.ofString());
            var body=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            if(response.statusCode()!=200 || !"00".equals(body.get("code")) || !(body.get("data") instanceof List<?> rows)) throw new IllegalArgumentException();
            var result=new ArrayList<Map<String,Object>>();
            for(Object entry:rows) if(entry instanceof Map<?,?> bank && config.allowedBins().contains(bank.get("bin"))
                && bank.get("transferSupported") instanceof Number supported && supported.intValue()==1) {
                result.add(Map.of("bin",bank.get("bin"),"name",Objects.toString(bank.get("name"),""),"shortName",Objects.toString(bank.get("shortName"),"")));
            }
            cached=List.copyOf(result);expires=Instant.now().plusSeconds(3600);return cached;
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable();}
        catch(java.io.IOException | IllegalArgumentException | tools.jackson.core.JacksonException ex){throw unavailable();}
    }
    public boolean supports(String bin){return banks().stream().anyMatch(row->bin.equals(row.get("bin")));}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa tải được danh mục ngân hàng thật. Vui lòng thử lại.");}
}
