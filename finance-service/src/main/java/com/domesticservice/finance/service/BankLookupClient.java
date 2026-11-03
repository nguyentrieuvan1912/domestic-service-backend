package com.domesticservice.finance.service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class BankLookupClient implements RecipientNameClient {
    private final String key,secret;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI base;
    private Map<String,String> codes=Map.of();
    private Instant expires=Instant.EPOCH;
    @Autowired
    public BankLookupClient(@Value("${payments.bank-lookup.api-key:}") String key,@Value("${payments.bank-lookup.api-secret:}") String secret,ObjectMapper json){
        this(key,secret,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),URI.create("https://api.banklookup.net"));
    }
    BankLookupClient(String key,String secret,ObjectMapper json,HttpClient http,URI base){this.key=key;this.secret=secret;this.json=json;this.http=http;this.base=base;}
    public boolean ready(){return key!=null && !key.isBlank() && secret!=null && !secret.isBlank();}
    public String provider(){return "BANKLOOKUP";}
    private Map<String,Object> call(String path,Map<String,Object> body){
        try{
            var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(12)).header("Accept","application/json");
            if(body==null)builder.GET();
            else builder.header("x-api-key",key).header("x-api-secret",secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==402)throw unavailable("Dịch vụ tra tên đã hết credit. Chưa xác minh tài khoản nhận tiền.");
            if(response.statusCode()==429)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Dịch vụ tra tên đang giới hạn lượt gọi. Vui lòng thử lại sau.");
            if(response.statusCode()==422)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chưa tìm thấy tài khoản nhận tại ngân hàng đã chọn.");
            if(response.statusCode()!=200)throw unavailable("Chưa kết nối được dịch vụ tra tên; kiểm tra khóa và quyền API.");
            var envelope=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            if(!Boolean.TRUE.equals(envelope.get("success")) || !(envelope.get("code") instanceof Number code) || code.intValue()!=200)
                throw unavailable("Dịch vụ chưa xác nhận kết quả tra cứu tài khoản.");
            return envelope;
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable("Tra tên bị gián đoạn. Vui lòng thử lại.");}
        catch(java.io.IOException | IllegalArgumentException | tools.jackson.core.JacksonException ex){throw unavailable("Chưa nhận được kết quả tra tên hợp lệ.");}
    }
    private synchronized String code(String bin){
        if(!Instant.now().isBefore(expires)){
            var envelope=call("/bank/list",null);
            if(!(envelope.get("data") instanceof List<?> rows))throw unavailable("Danh mục ngân hàng tra cứu không hợp lệ.");
            var next=new HashMap<String,String>();
            for(var item:rows)if(item instanceof Map<?,?> bank && bank.get("lookup_supported") instanceof Number supported && supported.intValue()==1){
                String id=Objects.toString(bank.get("bin"),""),code=Objects.toString(bank.get("code"),"");
                if(!id.matches("[0-9]{6}") || !code.matches("[A-Za-z0-9_-]{1,30}"))continue;
                if(next.putIfAbsent(id,code)!=null)throw unavailable("Danh mục ngân hàng tra cứu có mã BIN bị trùng.");
            }
            codes=Map.copyOf(next);expires=Instant.now().plusSeconds(3600);
        }
        var code=codes.get(bin);
        if(code==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngân hàng này chưa hỗ trợ tra tên tự động. Vui lòng chọn ngân hàng khác.");
        return code;
    }
    public Map<String,Object> lookup(String bin,String account){
        if(!ready())throw unavailable("Chưa cấu hình API Key và API Secret cho dịch vụ tra tên BankLookup.");
        if(!bin.matches("[0-9]{6}") || !account.matches("[0-9]{5,30}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngân hàng hoặc số tài khoản không hợp lệ.");
        String code=code(bin);
        var envelope=call("/",Map.of("bank",code,"account",account));
        if(!(envelope.get("data") instanceof Map<?,?> data))throw unavailable("Dịch vụ chưa trả thông tin chủ tài khoản.");
        String name=Objects.toString(data.get("ownerName"),"").strip();
        if(name.isBlank() || name.length()>160 || (data.containsKey("account") && !account.equals(data.get("account")))
            || (data.containsKey("bank") && !code.equals(data.get("bank")) && !bin.equals(Objects.toString(data.get("bank"),""))))
            throw unavailable("Kết quả tra tên không hợp lệ hoặc không khớp tài khoản đã yêu cầu.");
        // The documented response can contain only ownerName. Store request context separately;
        // do not claim the provider independently echoed a BIN/account that it did not return.
        return Map.of("accountHolder",name,"proof",Map.of("request",Map.of("bankBin",bin,"bankCode",code,"accountNumber",account),"response",envelope));
    }
    private static ResponseStatusException unavailable(String message){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}
