package com.domesticservice.booking.application;

import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.domesticservice.booking.application.BookingOperationsService.*;

/** A response window is server-owned; expiry and acceptance serialize on the booking row. */
@Service
public class StaffResponseService {
    static final String TIMEOUT_REASON="Hết thời gian nhận đơn";
    private final JdbcTemplate db;
    private final BookingOperationsService ops;
    private final BookingWorkflowService flow;
    private final TransactionTemplate tx;
    public StaffResponseService(JdbcTemplate db,BookingOperationsService ops,BookingWorkflowService flow,PlatformTransactionManager manager) {
        this.db=db;this.ops=ops;this.flow=flow;this.tx=new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay=1000,initialDelay=10000)
    public void expireInvitations() {
        // Small bounded batches; each booking commits separately and survives application restarts.
        var ids=db.queryForList("SELECT id FROM bookings WHERE staff_wait_state='WAITING' AND status='STAFF_ASSIGNED' AND staff_response_deadline<=now() ORDER BY staff_response_deadline LIMIT 50",Long.class);
        for(long id:ids)tx.executeWithoutResult(status->{
            var rows=db.queryForList("SELECT * FROM bookings WHERE id=? FOR UPDATE SKIP LOCKED",id);
            if(!rows.isEmpty())expire(rows.getFirst());
        });
        db.update("UPDATE bookings SET staff_wait_state='CLOSED' WHERE staff_wait_state='EXPIRED' AND starts_at<=now()");
    }

    private void expire(Map<String,Object>b) {
        if(!"WAITING".equals(b.get("staff_wait_state"))||!"STAFF_ASSIGNED".equals(b.get("status"))||((Timestamp)b.get("staff_response_deadline")).toInstant().isAfter(Instant.now()))return;
        long id=((Number)b.get("id")).longValue();
        db.update("UPDATE booking_assignments SET status='CANCELLED',execution_status='CANCELLED',cancellation_reason=? WHERE booking_id=? AND status='ASSIGNED'",TIMEOUT_REASON,id);
        db.update("UPDATE staff_reservations SET status='CANCELLED' WHERE booking_id=? AND status='HELD'",id);
        db.update("UPDATE bookings SET staff_wait_state='EXPIRED',cancel_reason=?,cancelled_by_type='SYSTEM',cancelled_at=now() WHERE id=?",TIMEOUT_REASON,id);
        flow.transition(b,"CANCELLED",((Number)b.get("customer_id")).longValue(),"SYSTEM",TIMEOUT_REASON);
    }

    public Map<String,Object> decide(String token,long id,Map<String,Object>body) {
        var actor=ops.actor(token,"CUSTOMER");long customer=ServiceHttpClient.userId(actor);
        String action=text(body,"action",10,true);
        if(!List.of("EXTEND","CANCEL","STOP").contains(action))throw error(HttpStatus.BAD_REQUEST,"Lựa chọn chờ không hợp lệ.");
        long round=num(body,"round",0,1,Integer.MAX_VALUE);
        return tx.execute(status->{
            // Same lock ordering as claim/create prevents competing bookings from reserving this staff.
            var invited=db.queryForList("SELECT a.* FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id WHERE b.id=? AND b.customer_id=? AND b.booking_mode='MODE_A' ORDER BY a.id DESC LIMIT 1",id,customer);
            if(invited.isEmpty())throw error(HttpStatus.NOT_FOUND,"Không tìm thấy lời mời thuộc tài khoản của bạn.");
            var assignment=invited.getFirst();long staff=((Number)assignment.get("staff_id")).longValue();ops.lockStaff(staff);
            var b=db.queryForMap("SELECT * FROM bookings WHERE id=? AND customer_id=? FOR UPDATE",id,customer);
            expire(b);b=db.queryForMap("SELECT * FROM bookings WHERE id=?",id);
            int current=((Number)b.get("staff_wait_round")).intValue();
            if("EXTEND".equals(action)&&current==round+1&&"WAITING".equals(b.get("staff_wait_state")))return flow.bookingView(b);
            if(!"EXTEND".equals(action)&&current==round&&"CLOSED".equals(b.get("staff_wait_state"))&&"CANCELLED".equals(b.get("status")))return flow.bookingView(b);
            if(current!=round)throw error(HttpStatus.CONFLICT,"Thời gian chờ đã được cập nhật. Vui lòng tải lại đơn.");
            if(!"EXPIRED".equals(b.get("staff_wait_state"))||!"CANCELLED".equals(b.get("status")))throw error(HttpStatus.CONFLICT,"Đơn chưa hết thời gian chờ hoặc đã được nhân viên nhận.");
            if(!"EXTEND".equals(action)) {
                String reason=TIMEOUT_REASON+("STOP".equals(action)?". Khách hàng hủy đặt dịch vụ, không tiếp tục đặt.":". Khách hàng chọn tìm nhân viên khác.");
                db.update("UPDATE bookings SET staff_wait_state='CLOSED',cancel_reason=?,cancelled_by=?,cancelled_by_type='CUSTOMER' WHERE id=?",reason,customer,id);
                flow.transition(b,"CANCELLED",customer,"CUSTOMER",reason);
            } else {
                var profile=ops.identityClient().list("/api/v1/identity/staff-directory",token).stream().filter(p->ServiceHttpClient.userId(p)==staff).findFirst().orElse(null);
                var candidate=new LinkedHashMap<>(b);candidate.put("booking_mode","MODE_B");
                if(!flow.eligibility(staff,candidate,profile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên hiện không còn phù hợp khung giờ này. Vui lòng tìm nhân viên khác.");
                for(var related:maps(ops.object(b.get("service_snapshot")).getOrDefault("relatedServices",List.of()))) {
                    var extra=new LinkedHashMap<>(candidate);extra.put("service_id",related.get("id"));
                    if(!flow.eligibility(staff,extra,profile).isEmpty())throw error(HttpStatus.CONFLICT,"Nhân viên không còn đủ điều kiện thực hiện dịch vụ bổ sung. Vui lòng tìm nhân viên khác.");
                }
                if(((Timestamp)b.get("starts_at")).toInstant().isBefore(Instant.now().plusSeconds(180)))throw error(HttpStatus.CONFLICT,"Không còn đủ 3 phút trước giờ làm. Vui lòng đặt lại thời gian khác.");
                ops.insert("INSERT INTO booking_assignments(booking_id,staff_id,status,staff_snapshot) VALUES (?,?,'ASSIGNED',CAST(? AS jsonb))",id,staff,Objects.toString(assignment.get("staff_snapshot"),"{}"));
                db.update("UPDATE bookings SET staff_response_deadline=clock_timestamp()+interval '3 minutes',staff_wait_state='WAITING',staff_wait_round=staff_wait_round+1,cancel_reason=NULL,cancelled_at=NULL,cancelled_by=NULL,cancelled_by_type=NULL WHERE id=?",id);
                flow.transition(b,"STAFF_ASSIGNED",customer,"CUSTOMER","Khách hàng xác nhận chờ thêm 3 phút.");
            }
            return flow.bookingView(db.queryForMap("SELECT * FROM bookings WHERE id=?",id));
        });
    }
}
