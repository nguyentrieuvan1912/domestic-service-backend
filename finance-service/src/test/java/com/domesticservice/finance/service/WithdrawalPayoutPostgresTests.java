package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayoutProperties;
import com.domesticservice.finance.repository.WithdrawalPayoutRepository;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Financial fixtures exist only in an isolated PostgreSQL, never the running application DB. */
@EnabledIfEnvironmentVariable(named="PAYOUT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=WithdrawalPayoutPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=${PAYOUT_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=payout_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class WithdrawalPayoutPostgresTests {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import({NativeQueryExecutor.class,WithdrawalPayoutRepository.class})
    static class Config {
        @Bean PayoutProperties payoutConfig(){return new PayoutProperties(true,"test-client","test-api","payout-test-key","source-only","TEST COMPANY","970452",2000,1000000);}
        @Bean(destroyMethod="stop") Peer peer(ObjectMapper json,PayoutProperties config)throws Exception{return new Peer(json,config);}
        @Bean PayoutBankDirectory directory(PayoutProperties config,ObjectMapper json){return new PayoutBankDirectory(config,json){@Override public List<Map<String,Object>> banks(){return List.of(Map.of("bin","970452","name","Test bank","shortName","TEST"));}};}
        @Bean WithdrawalPayoutService payouts(WithdrawalPayoutRepository repo,PayoutProperties config,Peer peer,PayoutBankDirectory directory,PlatformTransactionManager manager,ObjectMapper json){return new WithdrawalPayoutService(repo,peer.client,config,directory,manager,json,peer.url());}
    }
    static class Peer {
        final HttpServer server;final PayosPayoutClient client;final ObjectMapper json;
        final Map<String,Map<String,Object>> proofs=new ConcurrentHashMap<>();final AtomicInteger creates=new AtomicInteger();
        volatile boolean loseCreate=false,badSignature=false;volatile String sourceAccount="source-only";
        Peer(ObjectMapper json,PayoutProperties config)throws Exception {
            this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);client=new PayosPayoutClient(config,json,HttpClient.newHttpClient(),URI.create(url()));
            server.createContext("/",exchange->{try {
                exchange.getResponseHeaders().set("Content-Type","application/json");
                String path=exchange.getRequestURI().getPath();Map<String,Object> data;
                if(path.contains("/auth/me")) {
                    String token=exchange.getRequestHeaders().getFirst("Authorization").replace("Bearer ","");
                    data=Map.of("id","admin".equals(token)?1L:Long.parseLong(token),"role","admin".equals(token)?"ADMIN":"STAFF");
                    byte[] payload=json.writeValueAsBytes(data);exchange.sendResponseHeaders(200,payload.length);exchange.getResponseBody().write(payload);exchange.close();return;
                }else if(path.endsWith("/balance")) data=Map.of("accountNumber",sourceAccount,"accountName","TEST COMPANY","currency","VND","balance","10000000");
                else if("POST".equals(exchange.getRequestMethod())) {
                    var body=json.readValue(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),new TypeReference<Map<String,Object>>(){});
                    if(!client.sign(body).equals(exchange.getRequestHeaders().getFirst("x-signature")) || exchange.getRequestHeaders().getFirst("x-idempotency-key")==null){exchange.sendResponseHeaders(400,-1);exchange.close();return;}
                    creates.incrementAndGet();String reference=body.get("referenceId").toString();
                    var item=new LinkedHashMap<String,Object>(body);item.put("id","txn_"+reference);item.put("toAccountName","NGUYEN VAN A");item.put("state","PROCESSING");
                    data=new LinkedHashMap<>(Map.of("id","payout_"+reference,"referenceId",reference,"approvalState","PROCESSING","transactions",List.of(item)));
                    proofs.put(reference,data);
                    if(loseCreate){loseCreate=false;exchange.sendResponseHeaders(503,-1);exchange.close();return;}
                }else if(path.equals("/v1/payouts")) {
                    String query=exchange.getRequestURI().getQuery();String reference=URLDecoder.decode(query.substring(query.indexOf('=')+1,query.indexOf('&')),StandardCharsets.UTF_8);
                    data=Map.of("payouts",proofs.containsKey(reference)?List.of(proofs.get(reference)):List.of());
                }else {String id=path.substring(path.lastIndexOf('/')+1);data=proofs.values().stream().filter(p->id.equals(p.get("id"))).findFirst().orElseThrow();}
                exchange.getResponseHeaders().set("x-signature",badSignature?"0".repeat(64):client.sign(data));
                byte[] payload=json.writeValueAsBytes(Map.of("code","00","data",data));exchange.sendResponseHeaders(200,payload.length);exchange.getResponseBody().write(payload);exchange.close();
            }catch(Exception ex){exchange.sendResponseHeaders(500,-1);exchange.close();}});server.start();
        }
        String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        @SuppressWarnings("unchecked") void state(String reference,String state){var old=proofs.get(reference);var item=new LinkedHashMap<>((Map<String,Object>)((List<?>)old.get("transactions")).getFirst());item.put("state",state);var next=new LinkedHashMap<>(old);next.put("approvalState",state.equals("SUCCEEDED")?"COMPLETED":state.equals("FAILED")?"FAILED":"PROCESSING");next.put("transactions",List.of(item));proofs.put(reference,next);}
        public void stop(){server.stop(0);}
    }
    @Autowired WithdrawalPayoutService service;@Autowired JdbcTemplate db;@Autowired Peer peer;
    long staff,id;
    @BeforeEach void prepare(){
        peer.loseCreate=false;peer.badSignature=false;peer.sourceAccount="source-only";peer.creates.set(0);peer.proofs.clear();
        staff=ThreadLocalRandom.current().nextLong(10000000,100000000);
        long wallet=db.queryForObject("INSERT INTO staff_balances(staff_id,available_balance,reserved_balance) VALUES (?,30000,10000) RETURNING id",Long.class,staff);
        long bank=db.queryForObject("INSERT INTO staff_bank_accounts(staff_id,bank_code,account_number,account_holder,status,verified_by,verified_at,verification_note) VALUES (?,'970452','123456789','NGUYEN VAN A','VERIFIED',1,now(),'Bank verification fixture') RETURNING id",Long.class,staff);
        id=db.queryForObject("INSERT INTO withdrawals(staff_id,balance_id,bank_account_id,bank_account_snapshot,amount,status) SELECT ?,?,?,to_jsonb(b),10000,'APPROVED' FROM staff_bank_accounts b WHERE id=? RETURNING id",Long.class,staff,wallet,bank,bank);
    }
    String reference(){return db.queryForObject("SELECT reference_id FROM withdrawal_payouts WHERE withdrawal_id=?",String.class,id);}
    long amount(String field){return db.queryForObject("SELECT "+field+" FROM staff_balances WHERE staff_id=?",Long.class,staff);}
    @Test void signatureMatchesIndependentNodeCryptoVector(){assertEquals("845126967bbd50d149baf31ba00360694b3b3226e86a5bde895726483845e197",peer.client.sign(Map.of("referenceId","CMW-test","amount",10000,"description","CM rút tiền & test","toBin","970452","toAccountNumber","123456789")));}
    @Test void successfulConcurrentReconciliationDebitsReservedExactlyOnce()throws Exception {
        assertEquals("PROCESSING",service.dispatch("Bearer admin",id,true).get("status"));assertEquals(10000,amount("reserved_balance"));
        peer.state(reference(),"SUCCEEDED");
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.refresh("Bearer admin",id));var b=pool.submit(()->service.refresh("Bearer admin",id));a.get();b.get();}
        assertEquals(0,amount("reserved_balance"));assertEquals(30000,amount("available_balance"));assertEquals(10000,amount("withdrawn_amount"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=? AND transaction_type='WITHDRAWAL'",Integer.class,id));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM payout_money_entries WHERE payout_id=(SELECT id FROM withdrawal_payouts WHERE withdrawal_id=?)",Integer.class,id));
        service.dispatch("Bearer admin",id,true);assertEquals(1,peer.creates.get());
    }
    @Test void lostSubmissionResponseOnlyLooksUpOriginalIntent(){peer.loseCreate=true;assertThrows(ResponseStatusException.class,()->service.dispatch("Bearer admin",id,true));String ref=reference();peer.state(ref,"SUCCEEDED");assertEquals("PAID",service.dispatch("Bearer admin",id,true).get("status"));assertEquals(ref,reference());assertEquals(1,peer.creates.get());}
    @Test void confirmedFailureReleasesHoldExactlyOnce(){service.dispatch("Bearer admin",id,true);peer.state(reference(),"FAILED");service.refresh("Bearer admin",id);service.refresh("Bearer admin",id);assertEquals(40000,amount("available_balance"));assertEquals(0,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));}
    @SuppressWarnings("unchecked") @Test void mismatchedRecipientOrAmountOrNameCannotSettle(){
        service.dispatch("Bearer admin",id,true);String ref=reference();peer.state(ref,"SUCCEEDED");var proof=peer.proofs.get(ref);var original=new LinkedHashMap<>((Map<String,Object>)((List<?>)proof.get("transactions")).getFirst());
        for(var mismatch:List.of(Map.of("toAccountNumber","wrong"),Map.of("amount",9999),Map.of("toAccountName","OTHER PERSON"),Map.of("toBin","970422"))){var item=new LinkedHashMap<String,Object>(original);item.putAll(mismatch);var changed=new LinkedHashMap<>(proof);changed.put("transactions",List.of(item));peer.proofs.put(ref,changed);assertEquals("REVIEW",service.refresh("Bearer admin",id).get("payoutStatus"));assertEquals(10000,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));}
    }
    @Test void invalidSourceOrSignatureCannotCreateOrSettle(){peer.sourceAccount="wrong-source";assertThrows(ResponseStatusException.class,()->service.dispatch("Bearer admin",id,true));assertEquals(0,peer.creates.get());assertEquals(0,db.queryForObject("SELECT count(*) FROM withdrawal_payouts WHERE withdrawal_id=?",Integer.class,id));peer.sourceAccount="source-only";service.dispatch("Bearer admin",id,true);peer.state(reference(),"SUCCEEDED");peer.badSignature=true;assertThrows(ResponseStatusException.class,()->service.refresh("Bearer admin",id));assertEquals(10000,amount("reserved_balance"));}
    @Test void authorizationApprovalAndBankEvidenceAreRequired(){assertThrows(ResponseStatusException.class,()->service.dispatch("Bearer "+staff,id,true));assertThrows(ResponseStatusException.class,()->service.dispatch("Bearer admin",id,false));db.update("UPDATE staff_bank_accounts SET verified_at=NULL WHERE staff_id=?",staff);assertThrows(ResponseStatusException.class,()->service.dispatch("Bearer admin",id,true));assertEquals(0,peer.creates.get());assertThrows(ResponseStatusException.class,()->service.refresh("Bearer "+(staff+1),id));}
    @Test void concurrentSubmissionOnlyCreatesOneProviderRequest()throws Exception {try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.dispatch("Bearer admin",id,true));var b=pool.submit(()->service.dispatch("Bearer admin",id,true));a.get();b.get();}assertEquals(1,peer.creates.get());}
    @Test void schedulerNeverInitiatesAnApprovedWithdrawal(){service.reconcilePending();assertEquals(0,peer.creates.get());assertEquals(0,db.queryForObject("SELECT count(*) FROM withdrawal_payouts WHERE withdrawal_id=?",Integer.class,id));}
}
