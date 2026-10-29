package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayosProperties;
import com.domesticservice.finance.dto.StaffTopupRequest;
import com.domesticservice.finance.repository.StaffTopupRepository;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated PostgreSQL with real Flyway migrations/transactions; HTTP peers are test fixtures only. */
@EnabledIfEnvironmentVariable(named="TOPUP_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=StaffTopupPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=${TOPUP_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=topup_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true","PAYOS_VIRTUAL_ACCOUNT_BANK_BIN=","PAYOS_VIRTUAL_ACCOUNT_NAME="})
class StaffTopupPostgresTests {
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @Import({NativeQueryExecutor.class,StaffTopupRepository.class})
    static class Config {
        @Bean(destroyMethod="stop") Peer peer(ObjectMapper json) throws Exception {return new Peer(json);}
        @Bean PayosClient provider(Peer peer,ObjectMapper json) {return peer.client;}
        @Bean StaffTopupService topups(StaffTopupRepository repo,PayosClient provider,PlatformTransactionManager manager,ObjectMapper json,Peer peer) {
            return new StaffTopupService(repo,provider,manager,json,peer.url());
        }
    }
    static class Peer {
        final HttpServer server;final PayosClient client;final ObjectMapper json;
        final Map<Long,Map<String,Object>> proofs=new ConcurrentHashMap<>();
        volatile boolean loseCreate=false;volatile String account="12345678";
        Peer(ObjectMapper json) throws Exception {
            this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            client=new PayosClient(new PayosProperties(true,"test-client","test-api","test-checksum","12345678","https://example.com/return","https://example.com/cancel"),json,HttpClient.newHttpClient(),URI.create(url()));
            server.createContext("/",exchange->{
                Map<String,Object> response;
                if(exchange.getRequestURI().getPath().contains("/auth/me")) {
                    String token=exchange.getRequestHeaders().getFirst("Authorization").replace("Bearer ","");
                    response=Map.of("id",Long.parseLong(token),"role","STAFF");
                } else if(exchange.getRequestMethod().equals("POST")) {
                    var body=json.readValue(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),new TypeReference<Map<String,Object>>(){});
                    long id=((Number)body.get("orderCode")).longValue(),amount=((Number)body.get("amount")).longValue();
                    var link=new LinkedHashMap<String,Object>(Map.of("orderCode",id,"amount",amount,"description",body.get("description"),"accountNumber",account,"accountName","Test peer","bin","970422","currency","VND","paymentLinkId","test-link-"+id,"checkoutUrl","https://pay.payos.vn/web/test-link-"+id));
                    link.put("qrCode","transport-fixture");
                    proofs.putIfAbsent(id,new LinkedHashMap<>(Map.of("id","test-link-"+id,"orderCode",id,"amount",amount,"amountPaid",0L,"amountRemaining",amount,"status","PENDING")));
                    if(loseCreate) {loseCreate=false;exchange.sendResponseHeaders(503,-1);exchange.close();return;}
                    response=Map.of("code","00","data",link,"signature",client.sign(link));
                } else {
                    String path=exchange.getRequestURI().getPath();long id=Long.parseLong(path.substring(path.lastIndexOf('/')+1));
                    var proof=proofs.get(id);
                    response=proof==null?Map.of("code","231"):Map.of("code","00","data",proof,"signature",client.sign(proof));
                }
                byte[] bytes=json.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
        }
        String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        void paid(long id,long received){var previous=proofs.get(id);var next=new LinkedHashMap<>(previous);next.put("status","PAID");next.put("amountPaid",received);next.put("amountRemaining",Math.max(0,((Number)previous.get("amount")).longValue()-received));proofs.put(id,next);}
        public void stop(){server.stop(0);}
    }
    @Autowired StaffTopupService service;
    @Autowired JdbcTemplate db;
    @Autowired Peer peer;
    long staff;
    @BeforeEach void prepare(){staff=ThreadLocalRandom.current().nextLong(10000000,100000000);peer.loseCreate=false;peer.account="12345678";peer.client.configureVirtualAccounts("","");}
    String token(){return "Bearer "+staff;}
    long create(){return ((Number)service.create(token(),new StaffTopupRequest(10000,"test-"+staff)).get("id")).longValue();}
    long balance(){return db.queryForObject("SELECT available_balance FROM staff_balances WHERE staff_id=?",Long.class,staff);}
    @Test void inAppQrOnlyExposesTheStoredVerifiedAndUnexpiredPendingIntent() {
        long id=create();
        var pending=service.list(token(),false).getFirst();
        assertEquals("transport-fixture",pending.get("qrCode"));
        assertEquals("12345678",pending.get("accountNumber"));
        assertEquals(db.queryForObject("SELECT qr_content FROM staff_topups WHERE id=?",String.class,id),pending.get("qrCode"));
        db.update("UPDATE staff_topups SET expires_at=now()-interval '1 second' WHERE id=?",id);
        assertNull(service.list(token(),false).getFirst().get("qrCode"));
        db.update("UPDATE staff_topups SET expires_at=now()+interval '1 minute',receiving_account_verified=false WHERE id=?",id);
        assertNull(service.list(token(),false).getFirst().get("qrCode"));
        db.update("UPDATE staff_topups SET receiving_account_verified=true,status='REVIEW' WHERE id=?",id);
        assertNull(service.list(token(),false).getFirst().get("qrCode"));
        peer.paid(id,10000);
        assertNull(service.refresh(token(),id,false).get("qrCode"));
        assertEquals(10000,balance());
    }
    @Test void onlyConfirmedMoneyCreditsOnceEvenWithConcurrentRefreshes() throws Exception {
        long id=create();assertEquals(0,balance());
        assertEquals(id,((Number)service.create(token(),new StaffTopupRequest(10000,"test-"+staff)).get("id")).longValue());
        assertThrows(ResponseStatusException.class,()->service.create(token(),new StaffTopupRequest(20000,"test-"+staff)));
        assertThrows(ResponseStatusException.class,()->service.refresh("Bearer "+(staff+1),id,false));
        peer.paid(id,10000);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->service.refresh(token(),id,false));var two=pool.submit(()->service.refresh(token(),id,false));one.get();two.get();
        }
        assertEquals(10000,balance());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM wallet_transactions WHERE idempotency_key=?",Integer.class,"topup:"+id));
        assertEquals(0,db.queryForObject("SELECT sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) FROM topup_money_entries WHERE topup_id=?",Long.class,id));
        assertEquals(0,db.queryForObject("SELECT total_earned FROM staff_balances WHERE staff_id=?",Long.class,staff));
    }
    @Test void partialAndExcessAmountsRequireReview() {
        long id=create();peer.paid(id,5000);assertEquals("REVIEW",service.refresh(token(),id,false).get("status"));assertEquals(0,balance());
        peer.paid(id,11000);assertEquals("REVIEW",service.refresh(token(),id,false).get("status"));assertEquals(0,balance());
    }
    @Test void lostCreateResponseRecoversSameIntent() {
        peer.loseCreate=true;assertThrows(ResponseStatusException.class,this::create);
        long id=create();assertEquals(1,db.queryForObject("SELECT count(*) FROM staff_topups WHERE staff_id=?",Integer.class,staff));
        var row=service.refresh(token(),id,false);assertNull(row.get("checkoutUrl"));assertEquals("REVIEW",row.get("status"));assertEquals(0,balance());
        peer.paid(id,10000);assertEquals("REVIEW",service.refresh(token(),id,false).get("status"));assertEquals(0,balance());
    }
    @Test void setupSampleAndForgedWebhookCannotCredit() {
        long id=create();var data=new LinkedHashMap<String,Object>(Map.of("orderCode",id,"amount",10000,"accountNumber","12345678","currency","VND","reference","fixture-"+id,"code","00"));
        assertThrows(ResponseStatusException.class,()->service.webhook(Map.of("success",true,"data",data,"signature","0".repeat(64))));
        service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));assertEquals(0,balance());
        data.put("orderCode",123L);service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));assertEquals(0,balance());
        data.put("orderCode",id);peer.paid(id,10000);service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));
        service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));assertEquals(10000,balance());
    }
    @Test void wrongReceivingAccountDoesNotExposeCheckoutOrCredit() {
        peer.account="99999999";assertThrows(ResponseStatusException.class,this::create);assertEquals(0,balance());
        assertNull(service.list(token(),false).getFirst().get("checkoutUrl"));
    }
    @Test void virtualAccountWebhookMustMatchTheExactAccountPersistedForItsTopup() {
        peer.client.configureVirtualAccounts("970422","Test peer");peer.account="virtual-first";
        long first=create();peer.account="virtual-second";
        long second=((Number)service.create(token(),new StaffTopupRequest(10000,"second-"+staff)).get("id")).longValue();
        assertEquals("virtual-first",service.list(token(),false).stream().filter(row->((Number)row.get("id")).longValue()==first).findFirst().orElseThrow().get("accountNumber"));
        var data=new LinkedHashMap<String,Object>(Map.of("orderCode",first,"amount",10000,"accountNumber","virtual-second","currency","VND","reference","virtual-test-"+first,"code","00"));
        peer.paid(first,10000);
        assertThrows(ResponseStatusException.class,()->service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data))));
        assertEquals(0,balance());data.put("accountNumber","virtual-first");
        service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));
        service.webhook(Map.of("success",true,"data",data,"signature",peer.client.sign(data)));
        assertEquals(10000,balance());assertEquals("PENDING",service.refresh(token(),second,false).get("status"));
    }
}
