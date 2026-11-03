package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayosProperties;
import com.domesticservice.finance.dto.StaffTopupRequest;
import com.domesticservice.finance.repository.CustomerTopupRepository;
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
@SpringBootTest(classes=CustomerWalletPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=${TOPUP_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=topup_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true","PAYOS_VIRTUAL_ACCOUNT_BANK_BIN=","PAYOS_VIRTUAL_ACCOUNT_NAME="})
class CustomerWalletPostgresTests {
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @Import({NativeQueryExecutor.class,CustomerTopupRepository.class})
    static class Config {
        @Bean(destroyMethod="stop") Peer peer(ObjectMapper json) throws Exception {return new Peer(json);}
        @Bean PayosClient provider(Peer peer,ObjectMapper json) {return peer.client;}
        @Bean CustomerWalletService wallet(NativeQueryExecutor db,PlatformTransactionManager manager,CustomerTopupService topups,Peer peer){return new CustomerWalletService(db,manager,topups,peer.url(),peer.url(),"test-internal");}
        @Bean CustomerTopupService topups(CustomerTopupRepository repo,PayosClient provider,PlatformTransactionManager manager,ObjectMapper json,Peer peer) {
            return new CustomerTopupService(repo,provider,manager,json,peer.url());
        }
    }
    static class Peer {
        final HttpServer server;final PayosClient client;final ObjectMapper json;
        final Map<Long,Map<String,Object>> books=new ConcurrentHashMap<>();
        final Map<Long,Map<String,Object>> proofs=new ConcurrentHashMap<>();
        volatile boolean loseCreate=false;volatile String account="12345678";
        Peer(ObjectMapper json) throws Exception {
            this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            client=new PayosClient(new PayosProperties(true,"test-client","test-api","test-checksum","12345678","https://example.com/return","https://example.com/cancel"),json,HttpClient.newHttpClient(),URI.create(url()));
            server.createContext("/",exchange->{
                Map<String,Object> response;
                if(exchange.getRequestURI().getPath().contains("/auth/me")) {
                    String token=exchange.getRequestHeaders().getFirst("Authorization").replace("Bearer ","");
                    response=Map.of("id",Long.parseLong(token),"role","CUSTOMER");
                } else if(exchange.getRequestURI().getPath().contains("/bookings/")) {
                    String[] parts=exchange.getRequestURI().getPath().split("/");
                    long id=Long.parseLong(parts[parts.length-1].equals("reconcile-payment")?parts[parts.length-2]:parts[parts.length-1]);
                    response=books.get(id);
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
    @Autowired CustomerTopupService service;
    @Autowired CustomerWalletService wallet;
    @Autowired JdbcTemplate db;
    @Autowired Peer peer;
    long staff;
    @BeforeEach void prepare(){staff=ThreadLocalRandom.current().nextLong(10000000,100000000);peer.loseCreate=false;peer.account="12345678";peer.client.configureVirtualAccounts("","");}
    String token(){return "Bearer "+staff;}
    long create(){return ((Number)service.create(token(),new StaffTopupRequest(10000,"test-"+staff)).get("id")).longValue();}
    long balance(){return db.queryForObject("SELECT coalesce((SELECT available_balance FROM customer_wallets WHERE customer_id=?),0)",Long.class,staff);}
    @Test void inAppQrOnlyExposesTheStoredVerifiedAndUnexpiredPendingIntent() {
        long id=create();
        var pending=service.list(token(),false).getFirst();
        assertEquals("transport-fixture",pending.get("qrCode"));
        assertEquals("12345678",pending.get("accountNumber"));
        assertEquals(db.queryForObject("SELECT qr_content FROM customer_topups WHERE id=?",String.class,id),pending.get("qrCode"));
        db.update("UPDATE customer_topups SET expires_at=now()-interval '1 second' WHERE id=?",id);
        assertNull(service.list(token(),false).getFirst().get("qrCode"));
        db.update("UPDATE customer_topups SET expires_at=now()+interval '1 minute',receiving_account_verified=false WHERE id=?",id);
        assertNull(service.list(token(),false).getFirst().get("qrCode"));
        db.update("UPDATE customer_topups SET receiving_account_verified=true,status='REVIEW' WHERE id=?",id);
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
        assertEquals(1,db.queryForObject("SELECT count(*) FROM customer_wallet_transactions WHERE idempotency_key=?",Integer.class,"customer-topup:"+id));
    }
    @Test void partialAmountCannotPayAndExcessIsCreditedToWallet() {
        long id=create();peer.paid(id,5000);assertEquals("REVIEW",service.refresh(token(),id,false).get("status"));assertEquals(0,balance());
        peer.paid(id,11000);assertEquals("PAID",service.refresh(token(),id,false).get("status"));assertEquals(11000,balance());
    }
    @Test void lostCreateResponseRecoversSameIntent() {
        peer.loseCreate=true;assertThrows(ResponseStatusException.class,this::create);
        long id=create();assertEquals(1,db.queryForObject("SELECT count(*) FROM customer_topups WHERE customer_id=?",Integer.class,staff));
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

    Map<String,Object> book(String method){return new LinkedHashMap<>(Map.of("id",staff,"customerId",staff,"totalAmount",10000L,"pricingConfirmed",true,"status","AWAITING_PAYMENT","paymentMethod",method));}
    @Test void insufficientWalletCannotCreatePayment(){peer.books.put(staff,book("WALLET"));assertThrows(ResponseStatusException.class,()->wallet.pay(token(),staff));assertEquals(0,balance());assertEquals(0,db.queryForObject("SELECT count(*) FROM customer_booking_payments WHERE booking_id=?",Integer.class,staff));}
    @Test void concurrentWalletPaymentAndRefundAreExactlyOnce() throws Exception {
        long topup=create();peer.paid(topup,10000);service.refresh(token(),topup,false);
        var book=book("WALLET");peer.books.put(staff,book);
        try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(()->wallet.pay(token(),staff));var two=pool.submit(()->wallet.pay(token(),staff));one.get();two.get();}
        assertEquals(0,balance());assertEquals(10000L,wallet.status(staff).get("paidAmount"));
        book.put("status","CANCELLED");
        try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(()->wallet.reconcile(staff));var two=pool.submit(()->wallet.reconcile(staff));one.get();two.get();}
        assertEquals(10000,balance());assertEquals(10000L,wallet.status(staff).get("refundedAmount"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM customer_wallet_transactions WHERE idempotency_key=?",Integer.class,"booking:"+staff+":refund"));
    }
    @Test void qrCannotSpendExistingBalanceBeforeProviderConfirmsAndLatePaymentRefunds(){
        var book=book("BANK_TRANSFER_QR");peer.books.put(staff,book);
        var result=wallet.pay(token(),staff);var topup=(Map<?,?>)result.get("topup");long id=((Number)topup.get("id")).longValue();
        assertEquals(0L,wallet.status(staff).get("paidAmount"));assertEquals(0,balance());
        peer.paid(id,5000);service.refresh(token(),id,false);assertEquals(0L,wallet.status(staff).get("paidAmount"));
        book.put("status","CANCELLED");peer.paid(id,10000);service.refresh(token(),id,false);
        assertEquals(0,balance());wallet.reconcile(staff);wallet.reconcile(staff);
        assertEquals(10000,balance());assertEquals(10000L,wallet.status(staff).get("refundedAmount"));
    }
    @Test void qrOverpaymentPaysOnlyOrderAndKeepsDifferenceInWallet(){
        peer.books.put(staff,book("BANK_TRANSFER_QR"));var result=wallet.pay(token(),staff);long id=((Number)((Map<?,?>)result.get("topup")).get("id")).longValue();
        peer.paid(id,12000);service.refresh(token(),id,false);assertEquals(2000,balance());assertEquals(10000L,wallet.status(staff).get("paidAmount"));
        peer.books.get(staff).put("status","NO_STAFF_FOUND");wallet.reconcile(staff);assertEquals(12000,balance());
    }
    @Test void walletOwnershipAndInternalKeyAreEnforced(){
        peer.books.put(staff,book("WALLET"));assertThrows(ResponseStatusException.class,()->wallet.pay("Bearer "+(staff+1),staff));
        assertThrows(ResponseStatusException.class,()->wallet.authorize("wrong"));wallet.authorize("test-internal");
    }
    @Test void walletReadsPersistedBalanceAndOnlyTheLoggedInCustomersLedger(){
        long id=create();peer.paid(id,10000);service.refresh(token(),id,false);
        var mine=wallet.wallet(token());assertEquals(10000L,mine.get("available"));
        assertEquals(1,((List<?>)mine.get("transactions")).size());
        var other=wallet.wallet("Bearer "+(staff+1));assertEquals(0L,other.get("available"));assertTrue(((List<?>)other.get("transactions")).isEmpty());
    }
}
