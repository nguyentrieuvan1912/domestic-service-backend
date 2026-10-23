package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayoutProperties;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(PayoutProperties.class)
public class PayosPayoutClient {
    private final PayoutProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI base;
    @org.springframework.beans.factory.annotation.Autowired
    public PayosPayoutClient(PayoutProperties config,ObjectMapper json) {
        this(config,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),URI.create("https://api-merchant.payos.vn"));
    }
    PayosPayoutClient(PayoutProperties config,ObjectMapper json,HttpClient http,URI base) {
        this.config=config;this.json=json;this.http=http;this.base=base;
    }
    public void requireReady() {
        if(!config.ready()) throw unavailable("Chưa cấu hình đầy đủ Kênh chi payOS, tài khoản nguồn và ngân hàng nhận.");
    }
    static String normalizeName(Object value) {
        return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replace('đ','d').replace('Đ','D').toUpperCase(Locale.ROOT).replaceAll("\\s+"," ").strip();
    }
    public Map<String,Object> source() {
        var source=call("GET","/v1/payouts-account/balance",null,null,true);
        if(!Objects.equals(source.get("accountNumber"),config.sourceAccount())
            || !normalizeName(source.get("accountName")).equals(normalizeName(config.sourceName()))
            || !"VND".equals(source.get("currency"))) throw unavailable("Tài khoản nguồn chi không khớp cấu hình đã xác nhận.");
        return source;
    }
    public Map<String,Object> create(Map<String,Object> body,String key) {return call("POST","/v1/payouts/",body,key,true);}
    public Map<String,Object> find(String reference) {
        var page=call("GET","/v1/payouts?referenceId="+encode(reference)+"&limit=10&offset=0",null,null,true);
        if(!(page.get("payouts") instanceof List<?> rows)) throw unavailable("Chưa xác minh được danh sách lệnh chi.");
        var found=rows.stream().filter(row->row instanceof Map<?,?> m && reference.equals(m.get("referenceId"))).toList();
        if(found.size()>1) throw unavailable("Có nhiều lệnh cùng mã tham chiếu, cần đối soát.");
        if(found.isEmpty()) return null;
        var item=(Map<?,?>)found.getFirst();
        String id=Objects.toString(item.get("id"),"");
        if(!id.matches("[A-Za-z0-9_-]{1,160}")) throw unavailable("Mã lệnh chi không hợp lệ.");
        return call("GET","/v1/payouts/"+id,null,null,true);
    }
    public Map<String,Object> estimate(Map<String,Object> body) {return call("POST","/v1/payouts/estimate-credit",body,null,false);}
    private static String encode(String s) {
        return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20").replace("%21","!")
            .replace("%27","'").replace("%28","(").replace("%29",")").replace("%7E","~");
    }
    private Object sorted(Object value) {
        if(value instanceof Map<?,?> map) {var next=new TreeMap<String,Object>();map.forEach((k,v)->next.put(k.toString(),sorted(v)));return next;}
        if(value instanceof List<?> list) return list.stream().map(this::sorted).toList();
        return value;
    }
    // Payout signatures use URI-encoded, recursively sorted data (unlike payment-link signatures).
    public String sign(Map<String,Object> data) {
        try {
            var entries=new TreeMap<>(data);
            String canonical=entries.entrySet().stream().map(e->{
                Object v=sorted(e.getValue());
                String text=v==null?"":v instanceof Map<?,?> || v instanceof List<?>?json.writeValueAsString(v):v.toString();
                return encode(e.getKey())+"="+encode(text);
            }).collect(Collectors.joining("&"));
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(config.checksumKey().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException ex) {throw new IllegalStateException(ex);}
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> call(String method,String path,Map<String,Object> body,String key,boolean signedResponse) {
        requireReady();
        try {
            var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(12))
                .header("x-client-id",config.clientId()).header("x-api-key",config.apiKey()).header("Content-Type","application/json");
            if(key!=null) request.header("x-idempotency-key",key);
            if(body==null) request.GET();else request.header("x-signature",sign(body)).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            // No automatic POST retries: lost responses are recovered by the persisted reference.
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw unavailable("Chưa xác minh được Kênh chi payOS; kiểm tra khóa, IP cho phép và nguồn chi.");
            var envelope=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            if(!"00".equals(envelope.get("code")) || !(envelope.get("data") instanceof Map<?,?>))
                throw unavailable("payOS chưa xác nhận yêu cầu chi; giữ nguyên mã giao dịch để đối soát.");
            var data=(Map<String,Object>)envelope.get("data");
            if(signedResponse) {
                String signature=response.headers().firstValue("x-signature").orElse("");
                if(!signature.matches("[0-9a-fA-F]{64}") || !MessageDigest.isEqual(HexFormat.of().parseHex(signature),HexFormat.of().parseHex(sign(data))))
                    throw unavailable("Chữ ký kết quả chi không hợp lệ; cần đối soát, chưa ghi đã chuyển tiền.");
            }
            return data;
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable("Kết nối Kênh chi bị gián đoạn; tra cứu lại cùng giao dịch.");}
        catch(java.io.IOException | IllegalArgumentException | tools.jackson.core.JacksonException ex){throw unavailable("Chưa nhận được kết quả Kênh chi; tra cứu lại cùng giao dịch.");}
    }
    private static ResponseStatusException unavailable(String message){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}
