package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class BookingFinanceBridge {
 private final NativeQueryExecutor db;private final ObjectProvider<BookingWorkflowService> flow;private final TransactionTemplate tx;
 private final RestClient finance;private final String key;
 public BookingFinanceBridge(NativeQueryExecutor db,ObjectProvider<BookingWorkflowService> flow,PlatformTransactionManager manager,@Value("${clients.finance.base-url}")String url,@Value("${INTERNAL_SERVICE_TOKEN:}")String key){
  this.db=db;this.flow=flow;this.tx=new TransactionTemplate(manager);this.key=key;
  var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(4)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(10));
  finance=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 private Map<String,Object> snapshot(long id){return flow.getObject().bookingView(db.queryForMap("SELECT * FROM bookings WHERE id=?",id));}
 public void reserve(long id,long staff){
  if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình kết nối ví nhân viên.");
  try{var result=finance.post().uri("/api/v1/finance/internal/job-settlement/reserve/"+staff).header("X-Service-Key",key).body(snapshot(id)).retrieve().body(Map.class);
   if(result==null || !(result.get("amount") instanceof Number))throw new IllegalStateException("Finance did not return a debit receipt");
   db.update("UPDATE booking_assignments SET cash_collection_amount=? WHERE booking_id=? AND staff_id=? AND status='ACCEPTED'",result.get("amount"),id,staff);}
  catch(org.springframework.web.client.RestClientResponseException ex){
   String message="Chưa trừ được ví để nhận đơn tiền mặt. Vui lòng kiểm tra số dư và thử lại.";
   try{var body=new tools.jackson.databind.ObjectMapper().readValue(ex.getResponseBodyAsString(),Map.class);message=Objects.toString(body.getOrDefault("detail",body.getOrDefault("message",message)),message);}catch(RuntimeException ignored){}
   throw new ResponseStatusException(HttpStatus.CONFLICT,message);
  }
 }
 public void enqueue(long id){db.update("UPDATE bookings SET finance_sync_required=true,finance_checked_at='epoch' WHERE id=?",id);}
 // Holding Booking's row lock across the request serializes reconciliation with accept/cancel/complete.
 // A successful Finance debit whose Booking transaction rolled back is compensated by the next CASH scan.
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3000,initialDelay=15000)
 public void reconcile(){
  if(key.isBlank())return;
  for(long id:db.queryForList("SELECT id FROM bookings WHERE finance_sync_required OR (payment_method='CASH' AND status NOT IN ('COMPLETED','CANCELLED','NO_STAFF_FOUND','REJECTED','REFUNDED','ABSENT')) ORDER BY finance_checked_at,id LIMIT 30",Long.class)){
   try{tx.executeWithoutResult(state->{
    db.queryForMap("SELECT id FROM bookings WHERE id=? FOR UPDATE",id);
    var book=snapshot(id);
    var result=finance.post().uri("/api/v1/finance/internal/job-settlement/reconcile").header("X-Service-Key",key).body(book).retrieve().body(Map.class);
    boolean paid=result!=null&&"PAID".equals(result.get("status"));
    if(result==null || (!paid && !"WAITING".equals(result.get("status"))) || ("COMPLETED".equals(book.get("status")) && !paid))throw new IllegalStateException("Finance has not confirmed settlement");
    db.update("UPDATE bookings SET finance_sync_required=false,finance_checked_at=now(),finance_sync_error=NULL,payment_status=CASE WHEN ? THEN 'PAID' ELSE payment_status END,finance_settled_at=CASE WHEN ? THEN coalesce(finance_settled_at,now()) ELSE finance_settled_at END WHERE id=?",paid,paid,id);
   });}catch(RuntimeException ex){tx.executeWithoutResult(state->db.update("UPDATE bookings SET finance_checked_at=now(),finance_sync_error=? WHERE id=?",Objects.toString(ex.getMessage(),"Finance unavailable").substring(0,Math.min(1500,Objects.toString(ex.getMessage(),"Finance unavailable").length())),id));}
  }
 }
}
