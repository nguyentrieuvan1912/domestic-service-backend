package com.domesticservice.finance.service;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Booking owns execution/evidence; authenticated, locked snapshots drive Finance's atomic ledger. */
@Service
public class AutomaticJobSettlement {
 private final NativeQueryExecutor db;
 private final FinanceOperationsService ops;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private StaffDisciplineService discipline;
 public AutomaticJobSettlement(NativeQueryExecutor db, FinanceOperationsService ops){this.db=db;this.ops=ops;}
 static long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
 @SuppressWarnings("unchecked") static List<Map<String,Object>> assignments(Map<String,Object> b){return (List<Map<String,Object>>)b.get("assignments");}
 private void lock(long booking){db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,booking);}
 private Map<String,Object> wallet(long staff){db.update("INSERT INTO staff_balances(staff_id) VALUES (?) ON CONFLICT DO NOTHING",staff);return db.queryForMap("SELECT * FROM staff_balances WHERE staff_id=? FOR UPDATE",staff);}
 private void entry(long wallet,String direction,String type,long amount,long ref,String key){if(amount>0)db.insert("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,?,'AVAILABLE',?,?,?,?)",wallet,direction,type,amount,ref,key);}
 private void release(Map<String,Object> d){
  var w=wallet(n(d,"staff_id"));long amount=n(d,"amount");
  entry(n(w,"id"),"CREDIT","CASH_JOB_RETURN",amount,n(d,"id"),"cash:"+d.get("id")+":release");
  db.update("UPDATE staff_balances SET available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?",amount,w.get("id"));
  db.update("UPDATE cash_job_debits SET status='RELEASED',updated_at=now() WHERE id=?",d.get("id"));
 }
 private void releaseInactive(Map<String,Object> book){
  var active=assignments(book).stream().filter(a->List.of("ACCEPTED","COMPLETED").contains(a.get("status"))).map(a->n(a,"id")).toList();
  boolean ended=List.of("CANCELLED","NO_STAFF_FOUND","REJECTED","REFUNDED","ABSENT").contains(book.get("status"));
  for(var d:db.queryForList("SELECT * FROM cash_job_debits WHERE booking_id=? AND status='DEBITED' ORDER BY staff_id,id FOR UPDATE",book.get("id")))if(ended||!active.contains(n(d,"assignment_id")))release(d);
 }
 @Transactional public Map<String,Object> reserve(Map<String,Object> book,long staff){
  long id=n(book,"id");lock(id);
  if(!"CASH".equals(book.get("paymentMethod")))throw conflict("Đơn không dùng tiền mặt.");
  releaseInactive(book);
  var a=assignments(book).stream().filter(x->n(x,"staffId")==staff&&"ACCEPTED".equals(x.get("status"))).findFirst().orElseThrow(()->conflict("Ca chưa được nhận."));
  var old=db.queryForList("SELECT * FROM cash_job_debits WHERE assignment_id=?",a.get("id"));
  if(!old.isEmpty()){if(!"DEBITED".equals(old.getFirst().get("status")))throw conflict("Khoản trừ ca đã kết thúc. Vui lòng tải lại đơn.");return Map.of("amount",old.getFirst().get("amount"));}
  int count=(int)n(book,"requiredStaffCount"),slot=0;
  var occupied=db.queryForList("SELECT slot FROM cash_job_debits WHERE booking_id=? AND status IN ('DEBITED','SETTLED')",Integer.class,id);
  while(occupied.contains(slot))slot++;
  if(slot>=count)throw conflict("Đơn đã đủ vị trí thu tiền mặt.");
  long total=n(book,"totalAmount"),amount=total/count+(slot<total%count?1:0);
  if(amount<=0)throw conflict("Giá trị phần việc phải lớn hơn 0đ.");
  var w=wallet(staff);
  if(n(w,"available_balance")<amount)throw conflict("Ví không đủ số dư để nhận đơn tiền mặt. Cần "+amount+"đ cho phần việc của bạn. Vui lòng nạp thêm tiền.");
  long debit=db.insert("INSERT INTO cash_job_debits(booking_id,assignment_id,staff_id,slot,amount,status) VALUES (?,?,?,?,?,'DEBITED')",id,a.get("id"),staff,slot,amount);
  entry(n(w,"id"),"DEBIT","CASH_JOB_DEBIT",amount,debit,"cash:"+debit+":debit");
  db.update("UPDATE staff_balances SET available_balance=available_balance-?,version=version+1,updated_at=now() WHERE id=?",amount,w.get("id"));
  return Map.of("amount",amount);
 }
 @Transactional public Map<String,Object> reconcile(Map<String,Object> book){
  long id=n(book,"id");lock(id);
  boolean cash="CASH".equals(book.get("paymentMethod"));
  if(cash)releaseInactive(book);
  if(!"COMPLETED".equals(book.get("status")))return Map.of("status","WAITING");
  var done=assignments(book).stream().filter(a->"COMPLETED".equals(a.get("status"))).toList();
  if(done.size()!=n(book,"requiredStaffCount"))throw conflict("Chưa đủ nhân viên hoàn thành.");
  if(cash){
   if(done.stream().anyMatch(a->a.get("cashReceivedAt")==null))throw conflict("Nhân viên chưa xác nhận đã thu tiền mặt.");
   var debits=db.queryForList("SELECT * FROM cash_job_debits WHERE booking_id=? AND status IN ('DEBITED','SETTLED') ORDER BY staff_id",id);
   if(debits.size()!=done.size()||debits.stream().mapToLong(d->n(d,"amount")).sum()!=n(book,"totalAmount")||done.stream().anyMatch(a->debits.stream().noneMatch(d->n(d,"assignment_id")==n(a,"id"))))throw conflict("Chưa ghi nhận đủ khoản trừ ví khi nhận đơn tiền mặt.");
   if(db.queryForList("SELECT id FROM payments WHERE booking_id=? AND status='PAID'",id).isEmpty())
    db.insert("INSERT INTO payments(booking_id,customer_id,payment_code,amount,payment_method,provider,status,paid_at,admin_note) VALUES (?,?,?,?,'CASH','STAFF_CASH_CONFIRMATION','PAID',now(),'Nhân viên xác nhận thu tiền khi hoàn thành ca; đã trừ ví lúc nhận đơn')",id,book.get("customerId"),"AUTO-CASH-"+id,book.get("totalAmount"));
  }
  var result=ops.settleBook(id,book);
  if(cash)db.update("UPDATE cash_job_debits SET status='SETTLED',updated_at=now() WHERE booking_id=? AND status='DEBITED'",id);
  if(discipline!=null)discipline.completed(book);
  return result;
 }
}
