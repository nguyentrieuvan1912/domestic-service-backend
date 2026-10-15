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
        Instant start=parseTime(text(body,"startsAt",50,true));CustomerBookingTimePolicy.validate(start,Instant.now());
        String method=text(body,"paymentMethod",30,true);if(!"CASH".equals(method))throw error(HttpStatus.SERVICE_UNAVAILABLE,"Thanh toán trực tuyến cần cấu hình PayOS/VietQR và xác minh giao dịch trước khi tạo đơn. Bạn có thể chọn tiền mặt nếu dịch vụ hỗ trợ.");
        String mode=text(body,"mode",10,true);if(!List.of("MODE_A","MODE_B").contains(mode))throw error(HttpStatus.BAD_REQUEST,"Hình thức đặt không hợp lệ.");
        long addressId=num(body,"addressId",0,1,Long.MAX_VALUE);var address=ops.identityClient().get("/api/v1/identity/customer/addresses/"+addressId,token);
        @SuppressWarnings("unchecked") var quote=(Map<String,Object>)ops.catalogClient().call("POST","/api/v1/catalog/quotes",token,body);
        Instant end=start.plusSeconds(((Number)quote.get("durationMinutes")).longValue()*60);validateWindow(start,end,true);
        @SuppressWarnings("unchecked") var snapshot=(Map<String,Object>)quote.get("snapshot");
        FavouriteStaffDurationPolicy.validate(mode,((Number)quote.get("durationMinutes")).intValue(),snapshot);
        if("MODE_A".equals(mode)&&((Number)snapshot.get("requiredStaffCount")).longValue()!=1)throw error(HttpStatus.BAD_REQUEST,"Mode A chỉ chọn một nhân viên; gói nhiều nhân viên cần Mode B.");
        long staff="MODE_A".equals(mode)?num(body,"staffId",0,1,Long.MAX_VALUE):0;
        Map<String,Object> selectedProfile=staff>0?ops.identityClient().list("/api/v1/identity/staff-directory",token).stream().filter(p->ServiceHttpClient.userId(p)==staff).findFirst().orElse(null):null;
        if(staff>0){var profile=selectedProfile;var candidate=Map.<String,Object>of("id",-1L,"service_id",quote.get("serviceId"),"starts_at",Timestamp.from(start),"ends_at",Timestamp.from(end),"address_snapshot",json.writeValueAsString(address),"required_staff_count",snapshot.get("requiredStaffCount"),"booking_mode","MODE_B");if(!flow.eligibility(staff,candidate,profile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên phải đăng ký đầy đủ lịch tuần trước, sau đó đáp ứng năng lực, khu vực và giờ khách đặt.");}
        long id=Objects.requireNonNull(tx.execute(status->{db.execute("SELECT pg_advisory_xact_lock("+customer+")");var previous=db.queryForList("SELECT id,request_hash FROM bookings WHERE customer_id=? AND request_key=?",customer,key);if(!previous.isEmpty()){if(!hash.equals(previous.getFirst().get("request_hash")))throw error(HttpStatus.CONFLICT,"Mã yêu cầu đã dùng với nội dung khác.");return((Number)previous.getFirst().get("id")).longValue();}
            CustomerBookingTimePolicy.validate(start,Instant.now());
            if(staff>0){ops.lockStaff(staff);var candidate=candidate(((Number)quote.get("serviceId")).longValue(),start,end,address);if(!flow.eligibility(staff,candidate,selectedProfile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên vừa thay đổi lịch hoặc đã có đơn trong khung giờ này. Vui lòng tìm lại nhân viên.");
                for(var related:maps(snapshot.getOrDefault("relatedServices",List.of())))if(!flow.eligibility(staff,candidate(((Number)related.get("id")).longValue(),start,end,address),selectedProfile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên chưa đủ điều kiện thực hiện dịch vụ kèm theo. Vui lòng chọn lại dịch vụ bổ sung.");
            }
            long created=ops.insert("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,package_id,address_id,quote_id,quote_version,service_snapshot,address_snapshot,starts_at,ends_at,required_staff_count,base_amount,add_on_amount,discount_amount,total_amount,promotion_id,promotion_code,payment_method,notes,request_key,request_hash,pricing_confirmed) VALUES (?,?,'PENDING',?,?,?,?,?,1,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,'CASH',?,?,?,false)","BK-"+LocalDate.now(ZONE).toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),mode,customer,quote.get("serviceId"),quote.get("packageId"),addressId,quote.get("id"),json.writeValueAsString(snapshot),json.writeValueAsString(address),Timestamp.from(start),Timestamp.from(end),snapshot.get("requiredStaffCount"),quote.get("baseAmount"),quote.get("addOnAmount"),quote.get("discountAmount"),quote.get("totalAmount"),snapshot.get("promotionId"),snapshot.get("promotionCode"),text(body,"notes",2000,false),key,hash);
            for(var add:maps(snapshot.get("addOns")))ops.insert("INSERT INTO booking_add_ons(booking_id,add_on_id,name_snapshot,quantity,unit_price,extra_duration_minutes,total_price) VALUES (?,?,?,1,?,?,?)",created,add.get("id"),add.get("name"),add.get("price"),add.get("durationMinutes"),add.get("price"));
            if(staff>0)ops.insert("INSERT INTO booking_assignments(booking_id,staff_id,status,staff_snapshot) VALUES (?,?,'ASSIGNED',CAST(? AS jsonb))",created,staff,json.writeValueAsString(selectedProfile));
            ops.insert("INSERT INTO booking_status_histories(booking_id,new_status,changed_by,changed_by_type,reason) VALUES (?,'PENDING',?,'CUSTOMER','Đang xác nhận báo giá chính thức.')",created,customer);
            ops.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'PRICE_CONFIRMATION_PENDING',CAST(? AS jsonb))",created,json.writeValueAsString(Map.of("quoteId",quote.get("id"))));return created;}));
        var persisted=flow.detail(token,id);if(Boolean.TRUE.equals(persisted.get("pricingConfirmed")))return persisted;
        try {
            ops.catalogClient().call("POST","/api/v1/catalog/quotes/"+quote.get("id")+"/consume",token,Map.of("bookingId",id));
            tx.executeWithoutResult(status->{var before=db.queryForMap("SELECT * FROM bookings WHERE id=? FOR UPDATE",id);if("CANCELLED".equals(before.get("status")))throw error(HttpStatus.CONFLICT,"Đơn đã được hủy.");db.update("UPDATE bookings SET pricing_confirmed=true,staff_response_deadline=CASE WHEN booking_mode='MODE_A' THEN now()+interval '3 minutes' END,staff_wait_state=CASE WHEN booking_mode='MODE_A' THEN 'WAITING' END,staff_wait_round=CASE WHEN booking_mode='MODE_A' THEN 1 ELSE 0 END WHERE id=?",id);flow.transition(before,mode.equals("MODE_A")?"STAFF_ASSIGNED":"MATCHING",customer,"CUSTOMER","Đã xác nhận báo giá, chờ nhân viên tiếp nhận.");db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE aggregate_id=? AND event_type='PRICE_CONFIRMATION_PENDING'",id);});
        } catch(RuntimeException ex){try{flow.cancel(token,id,Map.of("reason","Không xác nhận được báo giá. Vui lòng tạo yêu cầu mới."));}catch(RuntimeException ignored){/* Pending outbox is retained for administrative reconciliation. */}throw ex;}
        return flow.detail(token,id);
    }
    public List<Map<String,Object>> staffSearch(String token,Long service,long addressId,String startsAt,int minutes){
        ops.actor(token,"CUSTOMER");Instant start=parseTime(startsAt);CustomerBookingTimePolicy.validate(start,Instant.now());
        if(minutes<30||minutes>720||minutes%30!=0)throw error(HttpStatus.BAD_REQUEST,"Thời lượng phải theo bước 30 phút, trong 30–720 phút.");
        Instant end=start.plusSeconds(minutes*60L);validateWindow(start,end,true);
        var address=ops.identityClient().get("/api/v1/identity/customer/addresses/"+addressId,token);
        var catalog=ops.services(token);if(service!=null&&!catalog.containsKey(service))throw error(HttpStatus.BAD_REQUEST,"Dịch vụ không khả dụng.");
        var profiles=ops.identityClient().list("/api/v1/identity/staff-directory",token);
        List<Map<String,Object>> result=new ArrayList<>();
        for(var profile:profiles){long staff=ServiceHttpClient.userId(profile);
            if(!ops.weekRegistered(staff,start))continue;
            var approved=db.queryForList("SELECT service_id FROM staff_service_capabilities WHERE staff_id=? AND status='APPROVED' AND approved_at<=now() AND (valid_from IS NULL OR valid_from<=?) AND (valid_until IS NULL OR valid_until>=?) ORDER BY service_id",Long.class,staff,Timestamp.from(start),Timestamp.from(end));
            List<Map<String,Object>> eligible=new ArrayList<>();
            for(long sid:approved){if(service!=null&&sid!=service||!catalog.containsKey(sid))continue;
                if(flow.eligibility(staff,candidate(sid,start,end,address),profile).isEmpty())eligible.add(Map.of("id",sid,"name",Objects.toString(catalog.get(sid).get("name"),"")));
            }
            if(eligible.isEmpty())continue;
            var item=new LinkedHashMap<String,Object>(profile);item.putAll(availabilityLimit(staff,start));item.put("eligibleServices",eligible);item.put("operatingDistricts",db.queryForList("SELECT COALESCE(NULLIF(ward,''),district) FROM staff_areas WHERE staff_id=? AND status='ACTIVE' ORDER BY is_primary DESC,id",String.class,staff));item.put("competencies",eligible.stream().map(skill->skill.get("name")).toList());
            var stats=db.queryForMap("SELECT coalesce(avg(rating),5) AS rating,count(*) AS reviews FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED')",staff);
            item.put("rating",stats.get("rating"));item.put("reviewCount",stats.get("reviews"));item.put("completedBookingsCount",db.queryForObject("SELECT count(*) FROM booking_assignments WHERE staff_id=? AND status='COMPLETED'",Long.class,staff));
            result.add(item);
        }
        return result;
    }
    Map<String,Object> availabilityLimit(long staff,Instant start){
        var local=start.atZone(ZONE);var date=java.sql.Date.valueOf(local.toLocalDate());var time=java.sql.Time.valueOf(local.toLocalTime());
        var ends=db.queryForList("SELECT end_time FROM staff_availabilities WHERE staff_id=? AND status='AVAILABLE' AND (available_date=? OR (available_date IS NULL AND day_of_week=?)) AND start_time<=? AND end_time>?",java.sql.Time.class,staff,date,local.getDayOfWeek().getValue()%7,time,time);
        var closing=local.toLocalDate().atTime(20,0).atZone(ZONE).toInstant();
        var limit=ends.stream().map(end->local.toLocalDate().atTime(end.toLocalTime()).atZone(ZONE).toInstant()).max(Comparator.naturalOrder()).orElse(closing);
        String reason="SCHEDULE_END";
        if(!limit.isBefore(closing)){limit=closing;reason="OPERATING_HOURS";}
        var next=db.queryForObject("SELECT min(b.starts_at) FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.staff_id=? AND a.status IN ('ASSIGNED','ACCEPTED','COMPLETED') AND b.status NOT IN ('CANCELLED','REFUNDED','REJECTED','NO_STAFF_FOUND') AND b.starts_at>?",Timestamp.class,staff,Timestamp.from(start));
        if(next!=null&&next.toInstant().minusSeconds(3600).isBefore(limit)){limit=next.toInstant().minusSeconds(3600);reason="NEXT_BOOKING";}
        return Map.of("availableUntil",limit.toString(),"availableUntilReason",reason);
    }
    private Map<String,Object> candidate(long service,Instant start,Instant end,Map<String,Object> address){return Map.of("id",-1L,"service_id",service,"starts_at",Timestamp.from(start),"ends_at",Timestamp.from(end),"address_snapshot",json.writeValueAsString(address),"required_staff_count",1,"booking_mode","MODE_B");}
    static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
