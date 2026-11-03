package com.domesticservice.finance.service;

import com.domesticservice.finance.repository.*;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SETTLEMENT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=AutomaticJobSettlementPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.datasource.url=${SETTLEMENT_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=settlement_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class AutomaticJobSettlementPostgresTests {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
 @Import({NativeQueryExecutor.class,FinanceOperationsSqlRepository.class,AutomaticJobSettlement.class})
 static class Config {
  @Bean CustomerWalletService customer(NativeQueryExecutor db,PlatformTransactionManager tx){return new CustomerWalletService(db,tx,null,"http://127.0.0.1:1","http://127.0.0.1:1","test-only");}
  @Bean FinanceOperationsService operations(FinanceOperationsSqlRepository repo,ObjectMapper json){return new FinanceOperationsService(repo,json,"http://127.0.0.1:1","http://127.0.0.1:1",null,null);}
 }
 @Autowired JdbcTemplate db;@Autowired AutomaticJobSettlement jobs;
 static long sequence=System.currentTimeMillis();long id,staff,assignment;
 @BeforeEach void setup(){id=++sequence;staff=++sequence;assignment=++sequence;fund(staff,20000);}
 void fund(long person,long amount){db.update("INSERT INTO staff_balances(staff_id,available_balance) VALUES (?,?)",person,amount);}
 long available(long person){return db.queryForObject("SELECT available_balance FROM staff_balances WHERE staff_id=?",Long.class,person);}
 Map<String,Object> member(long a,long person,boolean done){var result=new LinkedHashMap<String,Object>(Map.of("id",a,"staffId",person,"status",done?"COMPLETED":"ACCEPTED"));if(done)result.put("cashReceivedAt","2026-10-30T10:00:00Z");return result;}
 Map<String,Object> book(String method,boolean done,long amount,List<Map<String,Object>> members){var b=new LinkedHashMap<String,Object>();b.putAll(Map.of("id",id,"customerId",99L,"status",done?"COMPLETED":"CONFIRMED","paymentMethod",method,"requiredStaffCount",members.size(),"totalAmount",amount,"packagePrice",amount,"addOnsTotal",0L,"discountAmount",0L));b.putAll(Map.of("assignments",members,"service",Map.of("name","Isolated settlement verification"),"address",Map.of(),"startsAt","2026-10-30T10:00:00Z","endsAt","2026-10-30T11:00:00Z"));return b;}
 Map<String,Object> single(boolean done){return book("CASH",done,10000,List.of(member(assignment,staff,done)));}
 void paid(String method,long amount){db.update("INSERT INTO payments(booking_id,customer_id,payment_code,amount,payment_method,provider,status,paid_at) VALUES (?,99,?,?,?,'TEST_ISOLATED','PAID',now())",id,"test-"+id,amount,method);}
 @Test void cashDebitThenEightyPercentReturnAndInvoiceAreIdempotent(){
  jobs.reserve(single(false),staff);jobs.reserve(single(false),staff);assertEquals(10000,available(staff));
  jobs.reconcile(single(true));jobs.reconcile(single(true));assertEquals(18000,available(staff));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM invoices WHERE booking_id=?",Integer.class,id));
  assertEquals(2000,db.queryForObject("SELECT platform_fee FROM staff_incomes WHERE booking_id=?",Long.class,id));
  assertEquals(8000,db.queryForObject("SELECT total_earned FROM staff_balances WHERE staff_id=?",Long.class,staff));
 }
 @Test void insufficientBalanceCannotAcceptAndCreatesNoLedger(){db.update("UPDATE staff_balances SET available_balance=1999 WHERE staff_id=?",staff);assertThrows(ResponseStatusException.class,()->jobs.reserve(single(false),staff));assertEquals(1999,available(staff));assertEquals(0,db.queryForObject("SELECT count(*) FROM cash_job_debits WHERE booking_id=?",Integer.class,id));}
 @Test void cancellationAndFailedClaimRefundFullDebitExactlyOnce(){jobs.reserve(single(false),staff);var b=single(false);b.put("assignments",List.of());b.put("status","MATCHING");jobs.reconcile(b);jobs.reconcile(b);assertEquals(20000,available(staff));assertEquals("RELEASED",db.queryForObject("SELECT status FROM cash_job_debits WHERE booking_id=?",String.class,id));}
 @Test void cashCompletionRequiresCollectionConfirmation(){jobs.reserve(single(false),staff);var b=single(true);b.put("assignments",List.of(Map.of("id",assignment,"staffId",staff,"status","COMPLETED")));assertThrows(ResponseStatusException.class,()->jobs.reconcile(b));assertEquals(10000,available(staff));}
 @Test void multiStaffSplitSumsToExactOrderAmount(){long other=++sequence,otherAssignment=++sequence;fund(other,20000);var b=book("CASH",false,10001,List.of(member(assignment,staff,false),member(otherAssignment,other,false)));jobs.reserve(b,staff);jobs.reserve(b,other);assertEquals(14999,available(staff));assertEquals(15000,available(other));b=book("CASH",true,10001,List.of(member(assignment,staff,true),member(otherAssignment,other,true)));jobs.reconcile(b);assertEquals(18999,available(staff));assertEquals(19000,available(other));assertEquals(10001,db.queryForObject("SELECT sum(service_amount) FROM staff_incomes WHERE booking_id=?",Long.class,id));}
 @Test void qrAndWalletRequireVerifiedFullPaymentBeforeAutomaticEarnings(){for(String method:List.of("BANK_TRANSFER_QR","WALLET")){id=++sequence;var b=book(method,true,10000,List.of(member(assignment,staff,true)));assertThrows(ResponseStatusException.class,()->jobs.reconcile(b));paid(method,10000);long before=available(staff);jobs.reconcile(b);jobs.reconcile(b);assertEquals(before+8000,available(staff));}}
 @Test void concurrentRetriesCannotDoubleDebitOrCredit()throws Exception{var pool=Executors.newFixedThreadPool(4);try{var pending=new ArrayList<Future<?>>();for(int i=0;i<4;i++)pending.add(pool.submit(()->jobs.reserve(single(false),staff)));for(var future:pending)future.get(15,TimeUnit.SECONDS);assertEquals(10000,available(staff));pending.clear();for(int i=0;i<4;i++)pending.add(pool.submit(()->jobs.reconcile(single(true))));for(var future:pending)future.get(15,TimeUnit.SECONDS);assertEquals(18000,available(staff));}finally{pool.shutdownNow();}}
 @Test void partialTransferCannotProduceIncome(){var b=book("BANK_TRANSFER_QR",true,10000,List.of(member(assignment,staff,true)));paid("BANK_TRANSFER_QR",9999);assertThrows(ResponseStatusException.class,()->jobs.reconcile(b));assertEquals(20000,available(staff));}
}
