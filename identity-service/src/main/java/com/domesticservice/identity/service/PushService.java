package com.domesticservice.identity.service;
import com.domesticservice.identity.repository.PushRepository;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.net.URI;
import java.net.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
@Service @EnableScheduling
public class PushService {
    private final AccountService accounts;private final PushRepository repository;private final ObjectMapper json;private final boolean enabled;private final String accessToken;private final TransactionTemplate tx;
    private final String bookingUrl,serviceKey;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public PushService(AccountService accounts,PushRepository repository,ObjectMapper json,PlatformTransactionManager manager,@Value("${EXPO_PUSH_ENABLED:false}") boolean enabled,@Value("${EXPO_PUSH_ACCESS_TOKEN:}") String accessToken,@Value("${clients.booking.base-url:http://localhost:8083}") String bookingUrl,@Value("${INTERNAL_SERVICE_TOKEN:}") String serviceKey){this.accounts=accounts;this.repository=repository;this.json=json;this.enabled=enabled;this.accessToken=accessToken;this.bookingUrl=bookingUrl;this.serviceKey=serviceKey;tx=new TransactionTemplate(manager);}
    @Transactional public Map<String,Object> register(String bearer,Map<String,Object> b){
        var user=accounts.authenticate(bearer,false);String token=Objects.toString(b.get("token"),""),platform=Objects.toString(b.get("platform"),"");
        if(!token.matches("(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{10,200}\\]") || !List.of("android","ios").contains(platform))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị nhận thông báo không hợp lệ.");
        try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bearer.substring(7).getBytes(StandardCharsets.UTF_8)));repository.register(user.id(),token,platform,hash);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        return Map.of("registered",true,"pushEnabled",enabled);
    }
    private static class ProviderHttpError extends Exception {final int code;ProviderHttpError(int code){this.code=code;}}
    private Map<String,Object> post(String path,Object body)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/"+path)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Accept","application/json");
        if(!accessToken.isBlank())builder.header("Authorization","Bearer "+accessToken);
        var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300)throw new ProviderHttpError(response.statusCode());
        return json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
    }
    private boolean valid(Map<String,Object> data,Map<String,Object> row)throws Exception{
        var body=new HashMap<String,Object>(data);body.put("userId",row.get("user_id"));
        var request=HttpRequest.newBuilder(URI.create(bookingUrl+"/api/v1/booking/internal/push-validity")).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Service-Key",serviceKey).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Validation unavailable");
        return Boolean.TRUE.equals(json.readValue(response.body(),new TypeReference<Map<String,Object>>(){}).get("valid"));
    }
    @SuppressWarnings("unchecked") @Scheduled(fixedDelay=5000,initialDelay=20000) public void dispatch(){
        if(!enabled)return;
        try{repository.queue();for(long id:repository.candidates("PENDING"))tx.executeWithoutResult(state->{
            var rows=repository.lock(id,"PENDING");if(rows.isEmpty())return;var row=rows.getFirst();
            if(Boolean.TRUE.equals(row.get("expired"))){repository.result(id,"EXPIRED",null,null,0);return;}
            try{
                var data=json.readValue(row.get("payload").toString(),new TypeReference<Map<String,Object>>(){});
                try { if(!valid(data,row)){repository.result(id,"EXPIRED",null,"BOOKING_NO_LONGER_VALID",0);return;} }
                catch(Exception validation){if(validation instanceof InterruptedException)Thread.currentThread().interrupt();repository.result(id,"PENDING",null,"VALIDATION_PENDING",15);return;}
                data.put("notificationId",row.get("notification_id").toString());data.put("type",row.get("notification_type"));
                var result=post("send",Map.of("to",row.get("token"),"title",row.get("title"),"body",row.get("content"),"data",data,"sound","default","priority","high","channelId","CALL_INCOMING".equals(row.get("notification_type"))?"booking-calls":"booking-reminders","ttl",60));
                var ticket=(Map<String,Object>)result.get("data");
                if(ticket!=null && "ok".equals(ticket.get("status")) && ticket.get("id")!=null)repository.result(id,"TICKET",ticket.get("id").toString(),null,30);
                else {String error=ticket==null?"INVALID_RESPONSE":Objects.toString(((Map<String,Object>)ticket.getOrDefault("details",Map.of())).get("error"),"PROVIDER_ERROR");repository.result(id,"FAILED",null,error,0);if("DeviceNotRegistered".equals(error))repository.disable(((Number)row.get("device_id")).longValue());}
            }catch(ProviderHttpError e){boolean retry=(e.code==429 || e.code>=500) && ((Number)row.get("attempts")).intValue()<5;repository.result(id,retry?"PENDING":"FAILED",null,"EXPO_HTTP_"+e.code,30);}
            catch(InterruptedException e){Thread.currentThread().interrupt();repository.result(id,"UNKNOWN",null,"INTERRUPTED",0);}
            catch(Exception e){repository.result(id,"UNKNOWN",null,"NO_CONFIRMED_TICKET",0); /* Never blindly replay an ambiguous send. */ }
        });}catch(RuntimeException ignored){ /* Retry pending database work; do not fabricate delivery. */ }
    }
    @SuppressWarnings("unchecked") @Scheduled(fixedDelay=30000,initialDelay=40000) public void receipts(){
        if(!enabled)return;
        try{for(long id:repository.candidates("TICKET"))tx.executeWithoutResult(state->{var rows=repository.lock(id,"TICKET");if(rows.isEmpty())return;var row=rows.getFirst();
            try{var result=post("getReceipts",Map.of("ids",List.of(row.get("ticket_id"))));var data=(Map<String,Object>)result.get("data");var receipt=data==null?null:(Map<String,Object>)data.get(row.get("ticket_id"));
                if(receipt==null){repository.result(id,((java.sql.Timestamp)row.get("created_at")).toInstant().isBefore(Instant.now().minusSeconds(86400))?"EXPIRED":"TICKET",null,null,60);return;}
                String error=Objects.toString(((Map<String,Object>)receipt.getOrDefault("details",Map.of())).get("error"),"");repository.result(id,"ok".equals(receipt.get("status"))?"ACCEPTED":"FAILED",null,error,0);if("DeviceNotRegistered".equals(error))repository.disable(((Number)row.get("device_id")).longValue());
            }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();repository.result(id,"TICKET",null,"RECEIPT_PENDING",60);}
        });}catch(RuntimeException ignored){}
    }
}
