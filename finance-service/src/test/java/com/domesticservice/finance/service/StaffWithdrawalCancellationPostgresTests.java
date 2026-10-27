package com.domesticservice.finance.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PAYOUT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=BankAccountReviewPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.datasource.url=${PAYOUT_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=payout_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class StaffWithdrawalCancellationPostgresTests {
 @Autowired FinanceOperationsService ops;@Autowired JdbcTemplate db;
 long staff,wallet,bank,id;String token;
 @BeforeEach void setup(){
  staff=ThreadLocalRandom.current().nextLong(300000000,400000000);token="Bearer staff"+staff;
  wallet=db.queryForObject("INSERT INTO staff_balances(staff_id,available_balance,reserved_balance) VALUES (?,10000,2000) RETURNING id",Long.class,staff);
  bank=db.queryForObject("INSERT INTO staff_bank_accounts(staff_id,bank_code,account_number,account_holder,status) VALUES (?,'970422','123456789','NGUYEN VAN A','VERIFIED') RETURNING id",Long.class,staff);
  id=db.queryForObject("INSERT INTO withdrawals(staff_id,balance_id,bank_account_id,bank_account_snapshot,amount,status) SELECT ?,?,?,to_jsonb(b),2000,'PENDING' FROM staff_bank_accounts b WHERE id=? RETURNING id",Long.class,staff,wallet,bank,bank);
 }
 long amount(String col){return db.queryForObject("SELECT "+col+" FROM staff_balances WHERE id=?",Long.class,wallet);}
 String status(){return db.queryForObject("SELECT status FROM withdrawals WHERE id=?",String.class,id);}
 int releases(){return db.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=? AND transaction_type='RELEASE'",Integer.class,id);}
 @Test void cancelRestoresActualBalanceAndRecordsActorTimestampLedgerAndEvent(){
  assertEquals("CANCELLED",ops.cancelWithdrawal(token,id).get("status"));assertEquals(12000,amount("available_balance"));assertEquals(0,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));assertEquals(2,releases());
  var row=db.queryForMap("SELECT cancelled_by,cancelled_at,cancellation_reason FROM withdrawals WHERE id=?",id);assertEquals(staff,row.get("cancelled_by"));assertNotNull(row.get("cancelled_at"));assertFalse(row.get("cancellation_reason").toString().isBlank());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='WITHDRAWAL_CANCELLED' AND payload->>'referenceId'=?",Integer.class,Long.toString(id)));
 }
 @Test void retriesAndConcurrentCancellationReleaseExactlyOnce()throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->ops.cancelWithdrawal(token,id));var b=pool.submit(()->ops.cancelWithdrawal(token,id));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}
  ops.cancelWithdrawal(token,id);assertEquals(12000,amount("available_balance"));assertEquals(0,amount("reserved_balance"));assertEquals(2,releases());
 }
 @Test void anotherStaffAndAdminCannotCancelStaffRequest(){
  assertEquals(404,assertThrows(ResponseStatusException.class,()->ops.cancelWithdrawal("Bearer staff"+(staff+1),id)).getStatusCode().value());
  assertEquals(403,assertThrows(ResponseStatusException.class,()->ops.cancelWithdrawal("Bearer admin",id)).getStatusCode().value());assertEquals("PENDING",status());assertEquals(0,releases());
 }
 @Test void approvedOrLaterRequestsCannotBeCancelled(){for(var state:List.of("APPROVED","PROCESSING","PAID","REJECTED","FAILED")){db.update("UPDATE withdrawals SET status=? WHERE id=?",state,id);assertEquals(409,assertThrows(ResponseStatusException.class,()->ops.cancelWithdrawal(token,id)).getStatusCode().value());assertEquals(10000,amount("available_balance"));assertEquals(2000,amount("reserved_balance"));assertEquals(0,releases());}}
 @Test void payoutIntentPreventsCancellationEvenWithInconsistentPendingStatus(){
  db.update("INSERT INTO withdrawal_payouts(withdrawal_id,reference_id,idempotency_key,status,initiated_by) VALUES (?,?,?,'PROCESSING',1)",id,UUID.randomUUID().toString(),UUID.randomUUID().toString());
  assertThrows(ResponseStatusException.class,()->ops.cancelWithdrawal(token,id));assertEquals("PENDING",status());assertEquals(0,releases());
 }
 @Test void missingReservedMoneyCannotCreateCredit(){db.update("UPDATE staff_balances SET reserved_balance=1000 WHERE id=?",wallet);assertThrows(ResponseStatusException.class,()->ops.cancelWithdrawal(token,id));assertEquals("PENDING",status());assertEquals(10000,amount("available_balance"));assertEquals(1000,amount("reserved_balance"));assertEquals(0,releases());}
 @Test void ledgerFailureRollsBackBalanceAndStatus(){
  db.update("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,'DEBIT','RESERVED','RELEASE',1,?,?)",wallet,id,"withdraw:"+id+":release-reserved");
  assertThrows(Exception.class,()->ops.cancelWithdrawal(token,id));assertEquals("PENDING",status());assertEquals(10000,amount("available_balance"));assertEquals(2000,amount("reserved_balance"));assertEquals(1,releases());
 }
 @Test void approvalAndCancellationHaveOnlyOneWinner()throws Exception {
  var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var cancel=pool.submit(()->{start.await();try{ops.cancelWithdrawal(token,id);}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}return null;});
   var approve=pool.submit(()->{start.await();try{ops.reviewWithdrawal("Bearer admin",id,Map.of("status","APPROVED"));}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}return null;});
   start.countDown();cancel.get(15,TimeUnit.SECONDS);approve.get(15,TimeUnit.SECONDS);
  }
  if("CANCELLED".equals(status())){assertEquals(12000,amount("available_balance"));assertEquals(0,amount("reserved_balance"));assertEquals(2,releases());}
  else {assertEquals("APPROVED",status());assertEquals(10000,amount("available_balance"));assertEquals(2000,amount("reserved_balance"));assertEquals(0,releases());}
 }
 @Test void rejectionAndCancellationCannotRefundTwice()throws Exception {
  var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var cancel=pool.submit(()->{start.await();try{ops.cancelWithdrawal(token,id);}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}return null;});
   var reject=pool.submit(()->{start.await();try{ops.reviewWithdrawal("Bearer admin",id,Map.of("status","REJECTED","reason","Không tiếp tục yêu cầu rút"));}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}return null;});
   start.countDown();cancel.get(15,TimeUnit.SECONDS);reject.get(15,TimeUnit.SECONDS);
  }
  assertTrue(List.of("CANCELLED","REJECTED").contains(status()));assertEquals(12000,amount("available_balance"));assertEquals(0,amount("reserved_balance"));assertEquals(2,releases());
 }
}
