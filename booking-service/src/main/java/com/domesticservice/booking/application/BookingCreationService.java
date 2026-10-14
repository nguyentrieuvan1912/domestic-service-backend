package com.domesticservice.booking.application;

import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static com.domesticservice.booking.application.BookingOperationsService.*;

@Service
public class BookingCreationService {
    private final BookingOperationsService ops;private final BookingWorkflowService flow;private final JdbcTemplate db;private final ObjectMapper json;private final TransactionTemplate tx;
    public BookingCreationService(BookingOperationsService ops,BookingWorkflowService flow,JdbcTemplate db,ObjectMapper json,PlatformTransactionManager manager){this.ops=ops;this.flow=flow;this.db=db;this.json=json;tx=new TransactionTemplate(manager);}
    public Map<String,Object> create(String token,Map<String,Object>body){
        long customer=ServiceHttpClient.userId(ops.actor(token,"CUSTOMER"));String key=text(body,"requestKey",160,true),hash=digest(json.writeValueAsString(body));
        var retry=db.queryForList("SELECT id,request_hash,pricing_confirmed FROM bookings WHERE customer_id=? AND request_key=?",customer,key);if(!retry.isEmpty()){var existing=retry.getFirst();if(!hash.equals(existing.get("request_hash")))throw error(HttpStatus.CONFLICT,"Mã yêu cầu đã dùng với nội dung khác.");if(Boolean.TRUE.equals(existing.get("pricing_confirmed")))return flow.detail(token,((Number)existing.get("id")).longValue());}
        String method=text(body,"paymentMethod",30,true);if(!"CASH".equals(method))throw error(HttpStatus.SERVICE_UNAVAILABLE,"Thanh toán trực tuyến cần cấu hình PayOS/VietQR và xác minh giao dịch trước khi tạo đơn. Bạn có thể chọn tiền mặt nếu dịch vụ hỗ trợ.");
        String mode=text(body,"mode",10,true);if(!List.of("MODE_A","MODE_B").contains(mode))throw error(HttpStatus.BAD_REQUEST,"Hình thức đặt không hợp lệ.");
        long addressId=num(body,"addressId",0,1,Long.MAX_VALUE);var address=ops.identityClient().get("/api/v1/identity/customer/addresses/"+addressId,token);
        @SuppressWarnings("unchecked") var quote=(Map<String,Object>)ops.catalogClient().call("POST","/api/v1/catalog/quotes",token,body);
        Instant start=parseTime(text(body,"startsAt",50,true)),end=start.plusSeconds(((Number)quote.get("durationMinutes")).longValue()*60);validateWindow(start,end,true);
        @SuppressWarnings("unchecked") var snapshot=(Map<String,Object>)quote.get("snapshot");
        if("MODE_A".equals(mode)&&((Number)snapshot.get("requiredStaffCount")).longValue()!=1)throw error(HttpStatus.BAD_REQUEST,"Mode A chỉ chọn một nhân viên; gói nhiều nhân viên cần Mode B.");
        long staff="MODE_A".equals(mode)?num(body,"staffId",0,1,Long.MAX_VALUE):0;
        if(staff>0){var profile=ops.identityClient().list("/api/v1/identity/staff-directory",token).stream().filter(p->ServiceHttpClient.userId(p)==staff).findFirst().orElse(null);var candidate=Map.<String,Object>of("id",-1L,"service_id",quote.get("serviceId"),"starts_at",Timestamp.from(start),"ends_at",Timestamp.from(end),"address_snapshot",json.writeValueAsString(address),"required_staff_count",snapshot.get("requiredStaffCount"),"booking_mode","MODE_B");if(!flow.eligibility(staff,candidate,profile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên không phù hợp năng lực, khu vực hoặc lịch đã chọn.");}
        long id=Objects.requireNonNull(tx.execute(status->{db.execute("SELECT pg_advisory_xact_lock("+customer+")");var previous=db.queryForList("SELECT id,request_hash FROM bookings WHERE customer_id=? AND request_key=?",customer,key);if(!previous.isEmpty()){if(!hash.equals(previous.getFirst().get("request_hash")))throw error(HttpStatus.CONFLICT,"Mã yêu cầu đã dùng với nội dung khác.");return((Number)previous.getFirst().get("id")).longValue();}
            long created=ops.insert("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,package_id,address_id,quote_id,quote_version,service_snapshot,address_snapshot,starts_at,ends_at,required_staff_count,base_amount,add_on_amount,discount_amount,total_amount,promotion_id,promotion_code,payment_method,notes,request_key,request_hash,pricing_confirmed) VALUES (?,?,'PENDING',?,?,?,?,?,1,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,'CASH',?,?,?,false)","BK-"+LocalDate.now(ZONE).toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),mode,customer,quote.get("serviceId"),quote.get("packageId"),addressId,quote.get("id"),json.writeValueAsString(snapshot),json.writeValueAsString(address),Timestamp.from(start),Timestamp.from(end),snapshot.get("requiredStaffCount"),quote.get("baseAmount"),quote.get("addOnAmount"),quote.get("discountAmount"),quote.get("totalAmount"),snapshot.get("promotionId"),snapshot.get("promotionCode"),text(body,"notes",2000,false),key,hash);
            for(var add:maps(snapshot.get("addOns")))ops.insert("INSERT INTO booking_add_ons(booking_id,add_on_id,name_snapshot,quantity,unit_price,extra_duration_minutes,total_price) VALUES (?,?,?,1,?,?,?)",created,add.get("id"),add.get("name"),add.get("price"),add.get("durationMinutes"),add.get("price"));
            if(staff>0)ops.insert("INSERT INTO booking_assignments(booking_id,staff_id,status) VALUES (?,?,'ASSIGNED')",created,staff);
            ops.insert("INSERT INTO booking_status_histories(booking_id,new_status,changed_by,changed_by_type,reason) VALUES (?,'PENDING',?,'CUSTOMER','Đang xác nhận báo giá chính thức.')",created,customer);
            ops.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'PRICE_CONFIRMATION_PENDING',CAST(? AS jsonb))",created,json.writeValueAsString(Map.of("quoteId",quote.get("id"))));return created;}));
        var persisted=flow.detail(token,id);if(Boolean.TRUE.equals(persisted.get("pricingConfirmed")))return persisted;
        try {
            ops.catalogClient().call("POST","/api/v1/catalog/quotes/"+quote.get("id")+"/consume",token,Map.of("bookingId",id));
            tx.executeWithoutResult(status->{var before=db.queryForMap("SELECT * FROM bookings WHERE id=? FOR UPDATE",id);if("CANCELLED".equals(before.get("status")))throw error(HttpStatus.CONFLICT,"Đơn đã được hủy.");db.update("UPDATE bookings SET pricing_confirmed=true WHERE id=?",id);flow.transition(before,mode.equals("MODE_A")?"STAFF_ASSIGNED":"MATCHING",customer,"CUSTOMER","Đã xác nhận báo giá, chờ nhân viên tiếp nhận.");db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE aggregate_id=? AND event_type='PRICE_CONFIRMATION_PENDING'",id);});
        } catch(RuntimeException ex){try{flow.cancel(token,id,Map.of("reason","Không xác nhận được báo giá. Vui lòng tạo yêu cầu mới."));}catch(RuntimeException ignored){/* Pending outbox is retained for administrative reconciliation. */}throw ex;}
        return flow.detail(token,id);
    }
    public List<Map<String,Object>> staffSearch(String token,long service,long addressId,String startsAt,int minutes){ops.actor(token,"CUSTOMER");var address=ops.identityClient().get("/api/v1/identity/customer/addresses/"+addressId,token);Instant start=parseTime(startsAt),end=start.plusSeconds(minutes*60L);validateWindow(start,end,true);var candidate=Map.<String,Object>of("id",-1L,"service_id",service,"starts_at",Timestamp.from(start),"ends_at",Timestamp.from(end),"address_snapshot",json.writeValueAsString(address),"required_staff_count",1,"booking_mode","MODE_B");return ops.identityClient().list("/api/v1/identity/staff-directory",token).stream().filter(p->flow.eligibility(ServiceHttpClient.userId(p),candidate,p).isEmpty()).toList();}
    static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
