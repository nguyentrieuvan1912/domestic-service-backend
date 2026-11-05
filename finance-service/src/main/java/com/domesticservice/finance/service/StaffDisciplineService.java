package com.domesticservice.finance.service;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import static com.domesticservice.finance.service.FinanceOperationsService.*;

@Service
public class StaffDisciplineService {
 private final NativeQueryExecutor db;private final ServiceHttpClient identity;private final TransactionTemplate tx;
 public StaffDisciplineService(NativeQueryExecutor db,@Value("${clients.identity.base-url}")String url,PlatformTransactionManager manager){this.db=db;identity=new ServiceHttpClient(url);tx=new TransactionTemplate(manager);}
 public Map<String,Object> policy(){var p=db.queryForMap("SELECT * FROM staff_discipline_policy WHERE id=1");if(p.get("effective_from") instanceof java.sql.Timestamp t)p.put("effective_from",t.toInstant().toString());return p;}
 public List<Map<String,Object>> adminList(String token){identity.authenticate(token,"ADMIN");return db.queryForList("SELECT * FROM staff_discipline_actions ORDER BY id DESC");}
 public Map<String,Object> adminPolicy(String token){identity.authenticate(token,"ADMIN");return policy();}
 public List<Map<String,Object>> list(String token){var user=identity.authenticate(token,"ADMIN","STAFF");return "ADMIN".equals(user.get("role"))?db.queryForList("SELECT * FROM staff_discipline_actions ORDER BY id DESC"):db.queryForList("SELECT * FROM staff_discipline_actions WHERE staff_id=? ORDER BY id DESC",ServiceHttpClient.userId(user));}
 @Transactional public Map<String,Object> savePolicy(String token,Map<String,Object>b){long admin=ServiceHttpClient.userId(identity.authenticate(token,"ADMIN"));var before=db.queryForMap("SELECT * FROM staff_discipline_policy WHERE id=1 FOR UPDATE");if(!(b.get("noShowEnabled") instanceof Boolean))throw bad("Cần chọn trạng thái kiểm tra quên đơn.");db.update("UPDATE staff_discipline_policy SET no_show_enabled=?,grace_minutes=?,penalty_amount=?,completion_bonus=?,updated_by=?,updated_at=now() WHERE id=1",b.get("noShowEnabled"),number(b,"graceMinutes",0,120),number(b,"penaltyAmount",0,1000000000),number(b,"completionBonus",0,1000000000),admin);var after=policy();db.insert("INSERT INTO staff_discipline_policy_audit(actor_id,before_value,after_value) VALUES (?,?,?)",admin,before.toString(),after.toString());return after;}
 @Transactional public Map<String,Object> manual(String token,Map<String,Object>b){long admin=ServiceHttpClient.userId(identity.authenticate(token,"ADMIN"));long staff=number(b,"staffId",1,Long.MAX_VALUE);if(identity.list("/api/v1/identity/admin/staff",token).stream().noneMatch(p->ServiceHttpClient.userId(p)==staff))throw bad("Không tìm thấy nhân viên.");String kind=text(b,"kind",30,true);if(!List.of("REWARD","PENALTY","WARNING","WARN_THEN_PENALTY").contains(kind))throw bad("Loại lệnh không hợp lệ.");long amount=kind.equals("WARNING")?0:number(b,"amount",1,1000000000);String code=text(b,"violationCode",60,true);if(!code.matches("[A-Z0-9_]+"))throw bad("Mã vi phạm chỉ gồm chữ in hoa, số và dấu gạch dưới.");return create(staff,null,kind,code,amount,text(b,"reason",2000,true),"ADMIN",text(b,"requestKey",160,true),admin,"");}
 // A per-staff advisory lock serializes warning counters and wallet mutations, including concurrent retries.
 public Map<String,Object> create(long staff,Long booking,String kind,String code,long amount,String reason,String source,String key,Long admin,String evidence){
  db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,-staff);
  var prior=db.queryForList("SELECT * FROM staff_discipline_actions WHERE request_key=?",key);
  if(!prior.isEmpty()){var old=prior.getFirst();if(((Number)old.get("staff_id")).longValue()!=staff || !Objects.equals(old.get("kind"),kind)||((Number)old.get("amount")).longValue()!=amount||!Objects.equals(old.get("reason"),reason)||!Objects.equals(old.get("violation_code"),code)||!Objects.equals(old.get("booking_id"),booking))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Mã yêu cầu đã dùng cho một lệnh khác.");return old;}
  Integer count=null;boolean warning=kind.equals("WARNING");
  if(kind.equals("WARNING")||kind.equals("WARN_THEN_PENALTY")){count=1+db.queryForObject("SELECT count(*) FROM staff_discipline_actions WHERE staff_id=? AND violation_code=? AND kind IN ('WARNING','WARN_THEN_PENALTY')",Integer.class,staff,code);warning=kind.equals("WARNING")||count<3||amount==0;}
  long id=db.insert("INSERT INTO staff_discipline_actions(staff_id,booking_id,kind,violation_code,violation_number,amount,reason,source,status,request_key,created_by,evidence) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",staff,booking,kind,code,count,amount,reason,source,warning?"WARNING":"PENDING_FUNDS",key,admin,evidence);
  notify(staff,id,warning?"Cảnh báo vi phạm lần "+count+": "+reason:"Đã tạo lệnh "+kind+" "+amount+"đ: "+reason);
  if(!warning)apply(id);
  return db.queryForMap("SELECT * FROM staff_discipline_actions WHERE id=?",id);
 }
 private void apply(long id){
  var a=db.queryForMap("SELECT * FROM staff_discipline_actions WHERE id=? FOR UPDATE",id);if(!"PENDING_FUNDS".equals(a.get("status")))return;
  long staff=((Number)a.get("staff_id")).longValue(),amount=((Number)a.get("amount")).longValue();boolean credit="REWARD".equals(a.get("kind"));
  db.update("INSERT INTO staff_balances(staff_id) VALUES (?) ON CONFLICT DO NOTHING",staff);var w=db.queryForMap("SELECT * FROM staff_balances WHERE staff_id=? FOR UPDATE",staff);
  if(!credit && ((Number)w.get("available_balance")).longValue()<amount)return;
  long adjustment=credit?db.insert("INSERT INTO rewards(staff_id,reward_type,period_start,period_end,amount,reason,status) VALUES (?,?,CURRENT_DATE,CURRENT_DATE,?,?,'APPLIED')",staff,"DISCIPLINE_"+id,amount,a.get("reason")):db.insert("INSERT INTO penalties(staff_id,booking_id,penalty_type,amount,reason,status,approved_by,request_key) VALUES (?,?,'DISCIPLINE',?,?,'APPLIED',?,?)",staff,a.get("booking_id"),amount,a.get("reason"),a.get("created_by"),"discipline:"+id);
  long ledger=db.insert("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,?,'AVAILABLE',?,?,?,?)",w.get("id"),credit?"CREDIT":"DEBIT",credit?"REWARD":"PENALTY",amount,adjustment,"discipline:"+id);
  db.update("UPDATE staff_balances SET available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?",credit?amount:-amount,w.get("id"));
  db.update("UPDATE staff_discipline_actions SET status='APPLIED',applied_at=now(),adjustment_id=?,ledger_id=? WHERE id=?",adjustment,ledger,id);
  notify(staff,id,(credit?"Đã cộng thưởng ":"Đã trừ phạt ")+amount+"đ. Lệnh #"+id);
 }
 public List<Map<String,Object>> policyHistory(String token){identity.authenticate(token,"ADMIN");return db.queryForList("SELECT * FROM staff_discipline_policy_audit ORDER BY id DESC");}
 // Called only for a trusted, committed Booking cancellation. Verified non-cash receipts are the source of truth.
 @Transactional public Map<String,Object> refundForgotten(Map<String,Object>b){long booking=number(b,"bookingId",1,Long.MAX_VALUE),customer=number(b,"customerId",1,Long.MAX_VALUE);db.update("INSERT INTO customer_wallets(customer_id) VALUES (?) ON CONFLICT DO NOTHING",customer);db.queryForMap("SELECT customer_id FROM customer_wallets WHERE customer_id=? FOR UPDATE",customer);
  long refunded=0;
  for(var p:db.queryForList("SELECT * FROM payments WHERE booking_id=? AND customer_id=? AND payment_method<>'CASH' AND status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') ORDER BY id FOR UPDATE",booking,customer)){
   long payment=((Number)p.get("id")).longValue(),paid=((Number)p.get("amount")).longValue();
   long done=db.queryForObject("SELECT coalesce(sum(amount),0) FROM refunds WHERE payment_id=? AND status='PROCESSED'",Long.class,payment);
   long pending=db.queryForObject("SELECT count(*) FROM refunds WHERE payment_id=? AND status IN ('PENDING','APPROVED','PROCESSING')",Long.class,payment);
   if(pending>0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Khoản hoàn khác đang xử lý; cần đối soát trước khi hoàn tiếp.");
   long remaining=paid-done;if(remaining<0)throw bad("Tổng hoàn vượt thanh toán; cần đối soát.");
   if(remaining>0){db.insert("INSERT INTO refunds(payment_id,amount,reason,status,transaction_code,completed_at,admin_note) VALUES (?,?,?,'PROCESSED',?,now(),'Hoàn vào ví khách hàng do nhân viên quên đơn')",payment,remaining,"Nhân viên quên đơn #"+booking,"forgotten-"+booking+"-"+payment);db.insert("INSERT INTO customer_wallet_transactions(customer_id,direction,transaction_type,amount,booking_id,idempotency_key) VALUES (?,'CREDIT','REFUND',?,?,?)",customer,remaining,booking,"forgotten:"+booking+":payment:"+payment);db.update("UPDATE customer_wallets SET available_balance=available_balance+?,updated_at=now() WHERE customer_id=?",remaining,customer);}
   db.update("UPDATE customer_booking_payments SET refunded=true,reconciled_at=NULL WHERE payment_id=?",payment);refunded+=paid;
  }return Map.of("status","RECONCILED","refundedAmount",refunded);
 }
 private void notify(long staff,long id,String message){var payload=new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("staffId",staff,"referenceId",id,"message",message));db.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('STAFF',?,'DISCIPLINE',CAST(? AS jsonb))",staff,payload);}
 @Transactional public Map<String,Object> noShow(Map<String,Object>b){long staff=number(b,"staffId",1,Long.MAX_VALUE),booking=number(b,"bookingId",1,Long.MAX_VALUE),assignment=number(b,"assignmentId",1,Long.MAX_VALUE);var prior=db.queryForList("SELECT * FROM staff_discipline_actions WHERE request_key=?", "forgotten-job:"+assignment);if(!prior.isEmpty()){var a=prior.getFirst();if(((Number)a.get("staff_id")).longValue()!=staff||((Number)a.get("booking_id")).longValue()!=booking)throw bad("Sự kiện không khớp.");return a;}long amount=((Number)policy().get("penalty_amount")).longValue();return create(staff,booking,"WARN_THEN_PENALTY","FORGOTTEN_JOB",amount,"Quá giờ bắt đầu nhưng chưa di chuyển tới nhà khách.","AUTOMATIC","forgotten-job:"+assignment,null,text(b,"evidence",4000,true));}
 @SuppressWarnings("unchecked") public void completed(Map<String,Object> book){var rule=policy();long amount=((Number)rule.get("completion_bonus")).longValue();if(amount<=0||!"COMPLETED".equals(book.get("status"))||java.time.Instant.parse(book.get("startsAt").toString()).isBefore(java.time.Instant.parse(rule.get("effective_from").toString())))return;for(var a:(List<Map<String,Object>>)book.get("assignments")){long staff=((Number)a.get("staffId")).longValue(),booking=((Number)book.get("id")).longValue();if(!"COMPLETED".equals(a.get("status")))continue;String key="completed-job:"+a.get("id");if(!db.queryForList("SELECT id FROM staff_discipline_actions WHERE request_key=?",key).isEmpty())continue;if(db.queryForList("SELECT id FROM staff_incomes WHERE staff_id=? AND booking_id=? AND status='SETTLED'",staff,booking).isEmpty())continue;create(staff,booking,"REWARD","JOB_COMPLETED",amount,"Thưởng hoàn thành đơn đã nghiệm thu và quyết toán.","AUTOMATIC",key,null,"Booking "+booking+", assignment "+a.get("id"));}}
 @Scheduled(fixedDelay=15000,initialDelay=20000) public void retry(){for(long id:db.queryForList("SELECT id FROM staff_discipline_actions WHERE status='PENDING_FUNDS' ORDER BY id LIMIT 100",Long.class))try{tx.executeWithoutResult(s->apply(id));}catch(RuntimeException ignored){/* Durable pending action retained for next attempt. */}}
}
