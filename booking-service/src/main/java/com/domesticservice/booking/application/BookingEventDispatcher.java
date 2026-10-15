package com.domesticservice.booking.application;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
@Component @EnableScheduling
public class BookingEventDispatcher {
    private final JdbcTemplate db;private final ObjectMapper json;private final ServiceHttpClient identity;private final String key;private final TransactionTemplate tx;private final BookingWorkflowService flow;
    public BookingEventDispatcher(JdbcTemplate db,ObjectMapper json,BookingWorkflowService flow,PlatformTransactionManager manager,@Value("${clients.identity.base-url}")String url,@Value("${INTERNAL_SERVICE_TOKEN:}")String key){this.db=db;this.json=json;this.flow=flow;identity=new ServiceHttpClient(url);this.key=key;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=5000,initialDelay=10000)public void dispatch(){if(key.isBlank())return;try{var rows=db.queryForList("SELECT id FROM outbox_events WHERE status IN ('PENDING','FAILED') AND attempts<20 AND event_type<>'PRICE_CONFIRMATION_PENDING' ORDER BY id LIMIT 10",Long.class);for(long id:rows)tx.executeWithoutResult(status->{var events=db.queryForList("SELECT * FROM outbox_events WHERE id=? AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED",id);if(events.isEmpty())return;var event=events.getFirst();try{var books=db.queryForList("SELECT * FROM bookings WHERE id=?",event.get("aggregate_id"));if(books.isEmpty()){db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?",id);return;}var b=books.getFirst();var payload=json.readValue(event.get("payload").toString(),new TypeReference<Map<String,Object>>(){});if("CHAT_MESSAGE".equals(event.get("event_type"))) {
                    identity.internalEvent(key,Map.of("eventKey","chat-message:"+payload.get("messageId"),"userIds",List.of(payload.get("recipientId")),"type","CHAT_MESSAGE","title","Tin nhắn mới từ "+payload.get("senderName"),"content","Bạn có tin nhắn mới về đơn "+b.get("booking_code")+". Nhấn để mở cuộc trò chuyện.","referenceId",payload.get("conversationId"),"data",Map.of("bookingId",b.get("id"),"conversationId",payload.get("conversationId"),"messageId",payload.get("messageId")),"bookingCreated",false));
                    db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?",id);return;
                }
                String state=Objects.toString(payload.get("status"),Objects.toString(event.get("event_type"))),code=Objects.toString(b.get("booking_code"));Set<Long> users=new LinkedHashSet<>();users.add(((Number)b.get("customer_id")).longValue());users.addAll(db.queryForList("SELECT staff_id FROM booking_assignments WHERE booking_id=? AND status IN ('ASSIGNED','ACCEPTED','COMPLETED','CANCELLED')",Long.class,b.get("id")));boolean created="PENDING".equals(payload.get("oldStatus"))&&List.of("MATCHING","STAFF_ASSIGNED").contains(state);
                if("MATCHING".equals(state)&&"MODE_B".equals(b.get("booking_mode"))){for(var staff:identity.list("/api/v1/identity/staff-directory",null)){long staffId=ServiceHttpClient.userId(staff);var prefs=db.queryForList("SELECT match_notifications FROM staff_schedule_settings WHERE staff_id=?",Boolean.class,staffId);if((prefs.isEmpty()||prefs.getFirst())&&flow.eligibility(staffId,b,staff).isEmpty())users.add(staffId);}}
                String type=created?"BOOKING_CREATED":state.equals("COMPLETED")?"SERVICE_COMPLETED":state.equals("CANCELLED")?"BOOKING_CANCELLED":state.equals("IN_PROGRESS")?"SERVICE_IN_PROGRESS":state.equals("CONFIRMED")?"STAFF_ACCEPTED":"SYSTEM";
                boolean expired=StaffResponseService.TIMEOUT_REASON.equals(payload.get("reason"));
                boolean renewed="STAFF_ASSIGNED".equals(state)&&"CANCELLED".equals(payload.get("oldStatus"));
                if(expired){
                    long customer=((Number)b.get("customer_id")).longValue();
                    identity.internalEvent(key,Map.of("eventKey","booking:"+id+":customer","userIds",List.of(customer),"type","BOOKING_CANCELLED","title","Nhân viên chưa nhận đơn "+code,"content","Nhân viên bạn chọn đang bận. Bạn có muốn chờ thêm 3 phút hoặc tìm nhân viên khác?","referenceId",b.get("id"),"data",Map.of("bookingId",b.get("id")),"bookingCreated",false));
                    users.remove(customer);
                    if(!users.isEmpty())identity.internalEvent(key,Map.of("eventKey","booking:"+id+":staff","userIds",users.stream().toList(),"type","BOOKING_CANCELLED","title","Hết thời gian nhận đơn "+code,"content","Lời mời đã hủy do hết thời gian nhận đơn. Hãy chú ý lời mời và phản hồi sớm hơn để có doanh thu tốt hơn.","referenceId",b.get("id"),"data",Map.of("bookingId",b.get("id")),"bookingCreated",false));
                }else identity.internalEvent(key,Map.of("eventKey","booking:"+id,"userIds",users.stream().toList(),"type",renewed?"BOOKING_CREATED":type,"title","Đơn "+code,"content",renewed?"Khách hàng chờ thêm 3 phút. Nhân viên vui lòng phản hồi lời mời nhận đơn.":"Trạng thái đơn "+code+": "+state+".","referenceId",b.get("id"),"data",Map.of("bookingId",b.get("id")),"bookingCreated",created||renewed));db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?",id);
            }catch(RuntimeException ex){db.update("UPDATE outbox_events SET status='FAILED',attempts=attempts+1 WHERE id=?",id);}});}catch(RuntimeException ignored){/* Database/identity outage is retried; no fabricated notification is emitted. */}}
}
