package com.domesticservice.booking.service;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@Service
public class LiveKitService {
    private final String url,key,secret;private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public LiveKitService(ObjectMapper json,@Value("${LIVEKIT_URL:}") String url,@Value("${LIVEKIT_API_KEY:}") String key,@Value("${LIVEKIT_API_SECRET:}") String secret){this.json=json;this.url=url;this.key=key;this.secret=secret;}
    public boolean ready(){return url.startsWith("wss://") && !key.isBlank() && !secret.isBlank();}
    public void requireReady(){if(!ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình LiveKit. Vui lòng hoàn tất cấu hình cuộc gọi.");}
    public String url(){return url;}
    String sign(Map<String,Object> body){
        try{
            var encoder=Base64.getUrlEncoder().withoutPadding();String data=encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))+"."+encoder.encodeToString(json.writeValueAsBytes(body));
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return data+"."+encoder.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("Không tạo được token cuộc gọi.",e);}
    }
    public String join(String room,long user,String name){requireReady();long now=Instant.now().getEpochSecond();return sign(Map.of("iss",key,"sub","user-"+user,"name",name,"iat",now,"nbf",now-5,"exp",now+60,"video",Map.of("roomJoin",true,"room",room,"canPublish",true,"canSubscribe",true,"canPublishData",false)));}
    public void create(String room){rpc("CreateRoom",room,Map.of("name",room,"max_participants",2,"empty_timeout",60,"departure_timeout",10));}
    public void close(String room,long caller,long callee){
        // LiveKit Cloud revokes participant tokens even if the participant already left.
        for(long user:List.of(caller,callee))rpc("RemoveParticipant",room,Map.of("room",room,"identity","user-"+user,"revoke_token_ts",Instant.now().getEpochSecond()+1));
        rpc("DeleteRoom",room,Map.of("room",room));
    }
    private void rpc(String method,String room,Map<String,Object> body){
        requireReady();long now=Instant.now().getEpochSecond();String token=sign(Map.of("iss",key,"iat",now,"nbf",now-5,"exp",now+60,"video",Map.of("roomCreate",true,"roomAdmin",true,"room",room)));
        try{
            var request=HttpRequest.newBuilder(URI.create("https://"+URI.create(url).getRawAuthority()+"/twirp/livekit.RoomService/"+method)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(("CreateRoom".equals(method) || response.statusCode()!=404) && (response.statusCode()<200 || response.statusCode()>=300))throw new IllegalStateException("LiveKit refused request");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Kết nối LiveKit bị gián đoạn.");}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không kết nối được LiveKit. Kiểm tra cấu hình và thử lại.");}
    }
}
