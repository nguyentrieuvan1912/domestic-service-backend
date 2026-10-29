package com.domesticservice.booking.service;
import com.domesticservice.booking.repository.BookingCallRepository;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import static com.domesticservice.booking.service.BookingOperationsService.error;
import tools.jackson.databind.ObjectMapper;
@Service
public class BookingCallService {
    private final BookingOperationsService ops;private final BookingCallRepository repository;private final LiveKitService livekit;private final ObjectMapper json;private final TransactionTemplate tx;
    public BookingCallService(BookingOperationsService ops,BookingCallRepository repository,LiveKitService livekit,ObjectMapper json,PlatformTransactionManager manager){this.ops=ops;this.repository=repository;this.livekit=livekit;this.json=json;this.tx=new TransactionTemplate(manager);}
    private Map<String,Object> view(Map<String,Object> call,Map<String,Object> c){return Map.of("id",call.get("id"),"conversationId",c.get("id"),"bookingId",c.get("booking_id"),"callerId",call.get("caller_id"),"calleeId",call.get("callee_id"),"status",call.get("status"));}
    public Map<String,Object> capabilities(String token){ops.actor(token,"STAFF","CUSTOMER");return Map.of("enabled",livekit.ready());}
    @Transactional public Map<String,Object> start(String token,long conversation,Map<String,Object> body){
        var user=ops.actor(token,"STAFF","CUSTOMER");long uid=ServiceHttpClient.userId(user);
        String key=Objects.toString(body.get("requestKey"),"");if(!key.matches("[A-Za-z0-9_-]{1,160}"))throw error(HttpStatus.BAD_REQUEST,"Mã cuộc gọi không hợp lệ.");
        var c=repository.conversation(conversation,uid,true);
        var previous=repository.existing(uid,key);if(!previous.isEmpty()){
            if(((Number)previous.getFirst().get("conversation_id")).longValue()!=conversation)throw error(HttpStatus.CONFLICT,"Mã gọi đã dùng cho đơn khác.");return view(previous.getFirst(),c);
        }
        if(!repository.eligible(c))throw error(HttpStatus.CONFLICT,"Chỉ gọi khi nhân viên đã nhận đơn và ca chưa kết thúc.");
        livekit.requireReady();
        if(!repository.active(conversation).isEmpty())throw error(HttpStatus.CONFLICT,"Đơn này đang có cuộc gọi. Hãy mở cuộc gọi đang diễn ra.");
        long callee=((Number)c.get(((Number)c.get("customer_id")).longValue()==uid?"staff_id":"customer_id")).longValue();
        if(repository.busy(uid,callee))throw error(HttpStatus.CONFLICT,"Bạn hoặc người nhận đang có cuộc gọi khác. Vui lòng thử lại sau.");
        String room="booking-call-"+UUID.randomUUID();livekit.create(room);
        long id=repository.create(conversation,uid,callee,room,key);
        var payload=Map.of("callId",id,"bookingId",c.get("booking_id"),"conversationId",conversation,"recipientId",callee,"callerId",uid,"callerName",Objects.toString(user.get("fullName"),"Người gọi"),"expiresAt",Instant.now().plusSeconds(60).toString());
        repository.event(((Number)c.get("booking_id")).longValue(),json.writeValueAsString(payload));return view(repository.find(id,false),c);
    }
    private Map<String,Object> owned(long id,long uid,boolean lock){var initial=repository.find(id,false);repository.conversation(((Number)initial.get("conversation_id")).longValue(),uid,lock);return repository.find(id,lock);}
    private boolean stale(Map<String,Object> call){return "RINGING".equals(call.get("status")) && ((java.sql.Timestamp)call.get("created_at")).toInstant().isBefore(Instant.now().minusSeconds(60));}
    @Transactional public Map<String,Object> get(String token,long id){
        long uid=ServiceHttpClient.userId(ops.actor(token,"STAFF","CUSTOMER"));var call=owned(id,uid,true);var c=repository.conversation(((Number)call.get("conversation_id")).longValue(),uid,false);
        if(List.of("RINGING","ACCEPTED").contains(call.get("status")) && (stale(call)||!repository.eligible(c))){repository.change(id,stale(call)?"MISSED":"ENDED");call=repository.find(id,false);}return view(call,c);
    }
    @Transactional public Map<String,Object> action(String token,long id,Map<String,Object> body){
        long uid=ServiceHttpClient.userId(ops.actor(token,"STAFF","CUSTOMER"));var call=owned(id,uid,true);String action=Objects.toString(body.get("action"),"");
        if(!List.of("ACCEPT","DECLINE","END").contains(action))throw error(HttpStatus.BAD_REQUEST,"Thao tác cuộc gọi không hợp lệ.");
        var c=repository.conversation(((Number)call.get("conversation_id")).longValue(),uid,false);
        if(!"END".equals(action) && uid!=((Number)call.get("callee_id")).longValue())throw error(HttpStatus.FORBIDDEN,"Chỉ người nhận được phản hồi cuộc gọi.");
        if("ACCEPT".equals(action)){
            if(!repository.eligible(c)||stale(call))throw error(HttpStatus.CONFLICT,"Cuộc gọi đã hết hạn hoặc đơn đã kết thúc.");
            if("RINGING".equals(call.get("status")))repository.change(id,"ACCEPTED");
            else if(!"ACCEPTED".equals(call.get("status")))throw error(HttpStatus.CONFLICT,"Cuộc gọi đã kết thúc.");
        }else if(List.of("RINGING","ACCEPTED").contains(call.get("status")))repository.change(id,"DECLINE".equals(action)?"DECLINED":"ENDED");
        return view(repository.find(id,false),c);
    }
    @Transactional public Map<String,Object> join(String token,long id){
        var user=ops.actor(token,"STAFF","CUSTOMER");long uid=ServiceHttpClient.userId(user);var call=owned(id,uid,true);var c=repository.conversation(((Number)call.get("conversation_id")).longValue(),uid,false);
        if(!repository.eligible(c)||stale(call)||!List.of("RINGING","ACCEPTED").contains(call.get("status")) || "RINGING".equals(call.get("status")) && uid!=((Number)call.get("caller_id")).longValue())throw error(HttpStatus.CONFLICT,"Cuộc gọi chưa được nhận hoặc đã kết thúc.");
        return Map.of("serverUrl",livekit.url(),"token",livekit.join(call.get("room_name").toString(),uid,Objects.toString(user.get("fullName"),"Thành viên")));
    }
    public List<Map<String,Object>> incoming(String token){long uid=ServiceHttpClient.userId(ops.actor(token,"STAFF","CUSTOMER"));return repository.incoming(uid);}
    @Scheduled(fixedDelay=5000,initialDelay=20000) public void cleanup(){
        try{for(long id:repository.maintenance())try{
            var call=tx.execute(state->{var row=repository.find(id,false);var c=repository.conversation(((Number)row.get("conversation_id")).longValue(),((Number)row.get("caller_id")).longValue(),true);row=repository.find(id,true);
                if(List.of("RINGING","ACCEPTED").contains(row.get("status"))){if(stale(row)||!repository.eligible(c)||((java.sql.Timestamp)row.get("created_at")).toInstant().isBefore(Instant.now().minusSeconds(7200)))repository.change(id,stale(row)?"MISSED":"ENDED");}
                return repository.find(id,false);});
            if(call!=null && !List.of("RINGING","ACCEPTED").contains(call.get("status"))){livekit.close(call.get("room_name").toString(),((Number)call.get("caller_id")).longValue(),((Number)call.get("callee_id")).longValue());repository.closed(id);}
        }catch(RuntimeException ignored){ /* Retry remote cleanup; never mark provider success on failure. */ }
        }catch(RuntimeException ignored){ /* Database is retried. */ }
    }
}
