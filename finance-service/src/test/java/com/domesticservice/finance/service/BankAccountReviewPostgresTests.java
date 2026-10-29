package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayoutProperties;
import com.domesticservice.finance.repository.FinanceOperationsSqlRepository;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PAYOUT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=BankAccountReviewPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.datasource.url=${PAYOUT_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=payout_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class BankAccountReviewPostgresTests {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import({NativeQueryExecutor.class,FinanceOperationsSqlRepository.class})
 static class Config {
  @Bean PayoutProperties limits(){return new PayoutProperties(false,"","","","","","",2000,1000000);}
  @Bean(destroyMethod="stop") IdentityPeer peer(ObjectMapper json)throws Exception{return new IdentityPeer(json);}
  @Bean PayoutBankDirectory directory(PayoutProperties limits,ObjectMapper json){return new PayoutBankDirectory(limits,json);}
  @Bean FinanceOperationsService ops(FinanceOperationsSqlRepository repo,ObjectMapper json,IdentityPeer peer,PayoutBankDirectory directory,PayoutProperties limits){return new FinanceOperationsService(repo,json,peer.url(),peer.url(),directory,limits);}
 }
 static class IdentityPeer {
  final HttpServer server;volatile String adminName="Admin xét duyệt";
  IdentityPeer(ObjectMapper json)throws Exception {
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/api/v1/identity/auth/me",exchange->{
    var token=exchange.getRequestHeaders().getFirst("Authorization");boolean admin=token!=null && token.startsWith("Bearer admin");
    long id=token!=null && token.matches("Bearer staff[0-9]+")?Long.parseLong(token.substring(12)):"Bearer admin2".equals(token)?2L:1L;
    byte[] bytes=json.writeValueAsBytes(Map.of("id",id,"role",admin?"ADMIN":"STAFF","fullName",adminName));
    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
   });server.start();
  }
  String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
  void stop(){server.stop(0);}
 }
 @Autowired FinanceOperationsService ops;@Autowired JdbcTemplate db;@Autowired IdentityPeer peer;
 long bank;
 final String note="Đối chiếu tên và số tài khoản trên ứng dụng ngân hàng";
 @BeforeEach void setup(){peer.adminName="Admin xét duyệt";long staff=ThreadLocalRandom.current().nextLong(100000000,200000000);bank=db.queryForObject("INSERT INTO staff_bank_accounts(staff_id,bank_code,account_number,account_holder) VALUES (?,'970422','123456789','NGUYEN VAN A') RETURNING id",Long.class,staff);}
 void approve(){ops.verifyBank("Bearer admin",bank,Map.of("status","VERIFIED","verificationNote",note));}
 @Test void approvalRecordsAuthenticatedActorAndAccountSnapshot(){
  ops.verifyBank("Bearer admin",bank,Map.of("status","VERIFIED","verificationNote",note,"adminId",999));
  var history=ops.bankHistory("Bearer admin",bank);assertEquals(1,history.size());var row=history.getFirst();
  assertEquals(1L,row.get("adminId"));assertEquals(peer.adminName,row.get("adminName"));assertEquals("PENDING",row.get("oldStatus"));assertEquals("VERIFIED",row.get("newStatus"));assertEquals("123456789",row.get("accountNumber"));assertEquals(note,row.get("verificationNote"));assertNotNull(row.get("reviewedAt"));assertEquals(false,row.get("legacy"));
  assertEquals("VERIFIED",db.queryForObject("SELECT status FROM staff_bank_accounts WHERE id=?",String.class,bank));
  var account=ops.adminBanks("Bearer admin").stream().filter(item->((Number)item.get("id")).longValue()==bank).findFirst().orElseThrow();assertEquals(1L,account.get("verifiedBy"));assertEquals(peer.adminName,account.get("verifiedByName"));
 }
 @Test void repeatingSameDecisionIsIdempotent(){approve();var time=db.queryForObject("SELECT verified_at FROM staff_bank_accounts WHERE id=?",Object.class,bank);approve();assertEquals(1,ops.bankHistory("Bearer admin",bank).size());assertEquals(time,db.queryForObject("SELECT verified_at FROM staff_bank_accounts WHERE id=?",Object.class,bank));}
 @Test void eachLaterDecisionRetainsPriorAuditAndActor(){
  approve();ops.verifyBank("Bearer admin2",bank,Map.of("status","INACTIVE","verificationNote","Tài khoản cần được ngừng sử dụng"));approve();
  var history=ops.bankHistory("Bearer admin",bank);assertEquals(3,history.size());assertEquals("INACTIVE",history.getFirst().get("oldStatus"));assertEquals(2L,history.get(1).get("adminId"));assertEquals("VERIFIED",history.getLast().get("newStatus"));
 }
 @Test void auditCannotBeUpdatedOrDeleted(){approve();var id=ops.bankHistory("Bearer admin",bank).getFirst().get("id");assertThrows(Exception.class,()->db.update("UPDATE bank_account_reviews SET admin_id=999 WHERE id=?",id));assertThrows(Exception.class,()->db.update("DELETE FROM bank_account_reviews WHERE id=?",id));assertEquals(1L,ops.bankHistory("Bearer admin",bank).getFirst().get("adminId"));}
 @Test void failureToSaveAuditCannotVerifyAccount(){peer.adminName="x".repeat(161);assertThrows(Exception.class,this::approve);assertEquals("PENDING",db.queryForObject("SELECT status FROM staff_bank_accounts WHERE id=?",String.class,bank));assertTrue(ops.bankHistory("Bearer admin",bank).isEmpty());}
 @Test void staffCannotReadOrChangeBankVerification(){assertThrows(ResponseStatusException.class,()->ops.bankHistory("Bearer staff",bank));assertThrows(ResponseStatusException.class,()->ops.verifyBank("Bearer staff",bank,Map.of("status","VERIFIED","verificationNote",note)));assertEquals("PENDING",db.queryForObject("SELECT status FROM staff_bank_accounts WHERE id=?",String.class,bank));}
 @Test void concurrentIdenticalApprovalWritesOneAudit()throws Exception {try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(this::approve);var b=pool.submit(this::approve);a.get();b.get();}assertEquals(1,ops.bankHistory("Bearer admin",bank).size());}
}
