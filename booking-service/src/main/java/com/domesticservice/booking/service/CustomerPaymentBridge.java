package com.domesticservice.booking.service;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
@Service
public class CustomerPaymentBridge {
 private final NativeQueryExecutor db;private final BookingWorkflowService flow;private final TransactionTemplate tx;private final BookingOperationsService ops;
 private final RestClient finance;private final String key;
 public CustomerPaymentBridge(NativeQueryExecutor db,BookingWorkflowService flow,BookingOperationsService ops,PlatformTransactionManager manager,@Value("${clients.finance.base-url}") String url,@Value("${INTERNAL_SERVICE_TOKEN:}") String key){
  this.db=db;this.flow=flow;this.ops=ops;this.tx=new TransactionTemplate(manager);this.key=key;
  var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(4)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(10));
  finance=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public void authorize(String supplied){if(key.isBlank() || !java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),Objects.toString(supplied,"").getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 public Map<String,Object> snapshot(long id){var rows=db.queryForList("SELECT * FROM bookings WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return flow.bookingView(rows.getFirst());}
 @SuppressWarnings("unchecked") public void reconcile(long id){
  var proof=finance.get().uri("/api/v1/finance/internal/customer-payments/"+id).header("X-Service-Key",key).retrieve().body(Map.class);
  long paid=((Number)proof.get("paidAmount")).longValue(),refunded=((Number)proof.get("refundedAmount")).longValue();
  var initial=db.queryForMap("SELECT * FROM bookings WHERE id=?",id);
  boolean awaiting="AWAITING_PAYMENT".equals(initial.get("status"));
  var profiles=awaiting && paid-refunded>=((Number)initial.get("total_amount")).longValue()?ops.identityClient().list("/api/v1/identity/staff-directory",null):List.<Map<String,Object>>of();
  var invited="MODE_A".equals(initial.get("booking_mode"))?db.queryForList("SELECT staff_id FROM booking_assignments WHERE booking_id=? AND status='ASSIGNED'",Long.class,id):List.<Long>of();
  tx.executeWithoutResult(state->{
   // Match creation/claim's staff -> booking lock order and recheck eligibility after acquiring it.
   if(awaiting && !invited.isEmpty())ops.lockStaff(invited.getFirst());
   var rows=db.queryForList("SELECT * FROM bookings WHERE id=? FOR UPDATE",id);if(rows.isEmpty())return;var b=rows.getFirst();
   long total=((Number)b.get("total_amount")).longValue();
   String payment=paid>0 && refunded>=paid?"REFUNDED":paid-refunded>=total && total>0?"PAID":"PENDING";
   if("REFUNDED".equals(b.get("payment_status")))payment="REFUNDED";
   db.update("UPDATE bookings SET payment_status=? WHERE id=?",payment,id);
   if("AWAITING_PAYMENT".equals(b.get("status")) && "PAID".equals(payment) && Boolean.TRUE.equals(b.get("pricing_confirmed"))){
    var candidate=new LinkedHashMap<>(b);candidate.put("booking_mode","MODE_B");
    boolean canMatch;
    if("MODE_A".equals(b.get("booking_mode")))canMatch=!invited.isEmpty() && profiles.stream().anyMatch(p->((Number)p.get("id")).longValue()==invited.getFirst() && flow.eligibility(invited.getFirst(),candidate,p).isEmpty());
    else canMatch=profiles.stream().filter(p->flow.eligibility(((Number)p.get("id")).longValue(),candidate,p).isEmpty()).count()>=((Number)b.get("required_staff_count")).longValue();
    if(!canMatch || ((java.sql.Timestamp)b.get("starts_at")).toInstant().isBefore(java.time.Instant.now().plusSeconds(300))){
     db.update("UPDATE booking_assignments SET status='CANCELLED',execution_status='CANCELLED',cancellation_reason='Không có nhân viên phù hợp sau thanh toán' WHERE booking_id=? AND status='ASSIGNED'",id);
     flow.transition(b,"NO_STAFF_FOUND",0,"SYSTEM","Không có đủ nhân viên phù hợp để kết nối dịch vụ; tiền được hoàn về ví.");return;
    }
    db.update("UPDATE bookings SET staff_response_deadline=CASE WHEN booking_mode='MODE_A' THEN now()+interval '5 minutes' END,staff_wait_state=CASE WHEN booking_mode='MODE_A' THEN 'WAITING' END,staff_wait_round=CASE WHEN booking_mode='MODE_A' THEN 1 ELSE 0 END WHERE id=?",id);
    flow.transition(b,"MODE_A".equals(b.get("booking_mode"))?"STAFF_ASSIGNED":"MATCHING",0,"SYSTEM","Finance đã xác minh đủ tiền thanh toán.");
   }
  });
 }
 // Unfilled matching and unpaid drafts end at the last safe dispatch time. Finance retries refunds independently.
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=10000,initialDelay=20000)
 public void expire(){for(long id:db.queryForList("SELECT id FROM bookings WHERE (status='MATCHING' OR (payment_method<>'CASH' AND status='AWAITING_PAYMENT')) AND starts_at<=now()+interval '5 minutes' ORDER BY starts_at LIMIT 30",Long.class))tx.executeWithoutResult(state->{
  var b=db.queryForMap("SELECT * FROM bookings WHERE id=? FOR UPDATE",id);
  if(!List.of("MATCHING","AWAITING_PAYMENT").contains(b.get("status")))return;
  db.update("UPDATE booking_assignments SET status='CANCELLED',execution_status='CANCELLED',cancellation_reason='Không kết nối đủ nhân viên trước giờ làm' WHERE booking_id=? AND status IN ('ASSIGNED','ACCEPTED')",id);
  db.update("UPDATE staff_reservations SET status='CANCELLED' WHERE booking_id=? AND status IN ('HELD','CONFIRMED')",id);
  db.update("UPDATE conversations SET status='CLOSED',closed_at=now() WHERE booking_id=?",id);
  flow.transition(b,"NO_STAFF_FOUND",0,"SYSTEM","Không kết nối đủ nhân viên trước giờ làm; hoàn tiền thanh toán về ví.");
 });}
}
