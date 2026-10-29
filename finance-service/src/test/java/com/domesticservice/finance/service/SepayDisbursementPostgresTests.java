package com.domesticservice.finance.service;

import com.domesticservice.finance.config.*;
import com.domesticservice.finance.repository.*;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

/** Isolated PostgreSQL only. No production wallets, provider credentials or bank transfers are used. */
@EnabledIfEnvironmentVariable(named="PAYOUT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=SepayDisbursementPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=${PAYOUT_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=payout_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class SepayDisbursementPostgresTests {
    static final String BANK="11111111-1111-4111-8111-111111111111";
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import({NativeQueryExecutor.class,WithdrawalPayoutRepository.class,SepayDisbursementRepository.class})
    static class Config {
        @Bean PayoutProperties limits(){return new PayoutProperties(false,"","","","","","",2000,1000000);}
        @Bean SepayProperties sepay(){return new SepayProperties(true,"test-only-token","a-long-isolated-test-secret-123456789",BANK,"1234567890","TEST OWNER","","");}
        @Bean(destroyMethod="stop") WithdrawalPayoutPostgresTests.Peer identity(ObjectMapper json,PayoutProperties limits)throws Exception{return new WithdrawalPayoutPostgresTests.Peer(json,limits);}
        @Bean TestClient client(SepayProperties config,ObjectMapper json){return new TestClient(config,json);}
        @Bean PayoutBankDirectory banks(PayoutProperties limits,ObjectMapper json){return new PayoutBankDirectory(limits,json){@Override public boolean supports(String bin){return "970452".equals(bin);}};}
        @Bean SepayDisbursementService service(SepayProperties config,PayoutProperties limits,TestClient client,WithdrawalPayoutRepository payouts,SepayDisbursementRepository repo,PayoutBankDirectory banks,ObjectMapper json,PlatformTransactionManager manager,WithdrawalPayoutPostgresTests.Peer identity){return new SepayDisbursementService(config,limits,client,payouts,repo,banks,json,manager,identity.url());}
    }
    static class TestClient extends SepayClient {
        volatile List<Map<String,Object>> proof=List.of();volatile boolean down=false;
        TestClient(SepayProperties config,ObjectMapper json){super(config,json);}
        @Override public Map<String,Object> source(){if(down)throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);return Map.of("accumulated",30000);}
        @Override public List<Map<String,Object>> candidates(String ref,String bank){if(down)throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);return proof;}
        @Override public Map<String,Object> transaction(String id){return proof.stream().filter(p->id.equals(p.get("id"))).findFirst().orElseThrow();}
    }
    @Autowired SepayDisbursementService service;@Autowired JdbcTemplate db;@Autowired TestClient client;@Autowired SepayProperties config;
    long staff,id;
    @BeforeEach void setup(){
        client.proof=List.of();client.down=false;staff=ThreadLocalRandom.current().nextLong(10000000,100000000);
        long wallet=db.queryForObject("INSERT INTO staff_balances(staff_id,available_balance,reserved_balance) VALUES (?,30000,10000) RETURNING id",Long.class,staff);
        long bank=db.queryForObject("INSERT INTO staff_bank_accounts(staff_id,bank_code,account_number,account_holder,status,verified_by,verified_at,verification_note) VALUES (?,'970452','123456789','NGUYEN VAN A','VERIFIED',1,now(),'Verified bank fixture') RETURNING id",Long.class,staff);
        id=db.queryForObject("INSERT INTO withdrawals(staff_id,balance_id,bank_account_id,bank_account_snapshot,amount,status) SELECT ?,?,?,to_jsonb(b),10000,'APPROVED' FROM staff_bank_accounts b WHERE id=? RETURNING id",Long.class,staff,wallet,bank,bank);
    }
    long amount(String column){return db.queryForObject("SELECT "+column+" FROM staff_balances WHERE staff_id=?",Long.class,staff);}
    Map<String,Object> proof(){
        var result=new LinkedHashMap<String,Object>();result.put("id",UUID.randomUUID().toString());result.put("bank_account_id",BANK);result.put("account_number","1234567890");
        result.put("bank_brand_name","TPBank");result.put("transfer_type","out");result.put("amount_in",0L);result.put("amount_out",10000L);
        result.put("transaction_content",service.detail("Bearer admin",id).get("content"));result.put("reference_number","FT"+UUID.randomUUID().toString().replace("-",""));
        result.put("transaction_date",LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));return result;
    }
    Map<String,Object> confirmBody(String reference){return Map.of("recipientChecked",true,"bankReference",reference,"recipientNote","Đã đối chiếu NGUYEN VAN A trên ứng dụng ngân hàng");}
    @Test void settlementNeedsBothActualMoneyAndRecipientAttestationAndIsExactlyOnce()throws Exception {
        var opened=service.prepare("Bearer admin",id);assertTrue(opened.get("qrCode").toString().startsWith("000201010212"));assertEquals(opened.get("reference"),opened.get("content"));
        var proof=proof();client.proof=List.of(proof);service.refresh("Bearer "+staff,id);
        assertEquals(10000,amount("reserved_balance"));assertEquals("PROCESSING",service.detail("Bearer admin",id).get("status"));
        assertFalse((Boolean)service.detail("Bearer admin",id).get("canTransfer"));
        service.confirm("Bearer admin",id,confirmBody(proof.get("reference_number").toString()));
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.refresh("Bearer admin",id));var b=pool.submit(()->service.refresh("Bearer admin",id));a.get();b.get();}
        assertEquals(0,amount("reserved_balance"));assertEquals(10000,amount("withdrawn_amount"));assertEquals(30000,amount("available_balance"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=? AND transaction_type='WITHDRAWAL'",Integer.class,id));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM payout_money_entries WHERE payout_id=(SELECT id FROM withdrawal_payouts WHERE withdrawal_id=?)",Integer.class,id));
        assertFalse(service.detail("Bearer admin",id).containsKey("qrCode"));
    }
    @Test void confirmationCannotSettleWithoutBankProofAndOutageKeepsHold(){service.prepare("Bearer admin",id);client.down=true;service.confirm("Bearer admin",id,confirmBody("FTWAIT"+UUID.randomUUID()));assertEquals(10000,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));assertNotNull(service.detail("Bearer admin",id).get("confirmedAt"));}
    @Test void mismatchesAndDuplicateTransfersCannotSettle(){
        service.prepare("Bearer admin",id);var original=proof();service.confirm("Bearer admin",id,confirmBody(original.get("reference_number").toString()));
        for(var mismatch:List.of(Map.of("account_number","wrong"),Map.of("amount_out",9999L),Map.of("amount_in",1L),Map.of("transfer_type","in"),Map.of("bank_account_id",UUID.randomUUID().toString()),Map.of("transaction_date","2020-01-01 00:00:00"),Map.of("reference_number","OTHER"))){
            var changed=new LinkedHashMap<String,Object>(original);changed.putAll(mismatch);client.proof=List.of(changed);service.refresh("Bearer admin",id);assertEquals(10000,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));
        }
        client.proof=List.of(original,new LinkedHashMap<>(original));service.refresh("Bearer admin",id);assertEquals("REVIEW",service.detail("Bearer admin",id).get("payoutStatus"));
    }
    @Test void ownerAndBankAuditAndApprovalAreEnforced(){
        assertThrows(ResponseStatusException.class,()->service.prepare("Bearer "+staff,id));assertThrows(ResponseStatusException.class,()->service.refresh("Bearer "+(staff+1),id));
        db.update("UPDATE staff_bank_accounts SET verified_at=NULL WHERE staff_id=?",staff);assertThrows(ResponseStatusException.class,()->service.prepare("Bearer admin",id));
        db.update("UPDATE staff_bank_accounts SET verified_at=now() WHERE staff_id=?",staff);service.prepare("Bearer admin",id);
        db.update("UPDATE withdrawal_payouts SET initiated_by=999 WHERE withdrawal_id=?",id);assertThrows(ResponseStatusException.class,()->service.prepare("Bearer admin",id));assertFalse((Boolean)service.detail("Bearer admin",id).get("canConfirm"));
    }
    @Test void concurrentPrepareOnlyCreatesOneLockedIntent()throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.prepare("Bearer admin",id));var b=pool.submit(()->service.prepare("Bearer admin",id));assertEquals(a.get().get("reference"),b.get().get("reference"));}
        assertEquals(1,db.queryForObject("SELECT count(*) FROM withdrawal_payouts WHERE withdrawal_id=?",Integer.class,id));
    }
    String sign(byte[] raw,String timestamp)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(config.webhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return "sha256="+HexFormat.of().formatHex(mac.doFinal(raw));}
    @Test void signedWebhookIsDurableDuplicateSafeAndNeverSettlesWallet()throws Exception {
        service.prepare("Bearer admin",id);String key=UUID.randomUUID().toString();byte[] raw=("{\"id\":\""+key+"\",\"transferType\":\"out\",\"transferAmount\":10000}").getBytes(StandardCharsets.UTF_8);
        String timestamp=Long.toString(Instant.now().getEpochSecond());service.webhook(raw,timestamp,sign(raw,timestamp));service.webhook(raw,timestamp,sign(raw,timestamp));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM sepay_webhook_events WHERE event_key=?",Integer.class,key));assertEquals(10000,amount("reserved_balance"));
        assertThrows(ResponseStatusException.class,()->service.webhook(raw,timestamp,"sha256="+"0".repeat(64)));
        String old=Long.toString(Instant.now().minusSeconds(600).getEpochSecond());assertThrows(ResponseStatusException.class,()->service.webhook(raw,old,sign(raw,old)));
        byte[] changed=new String(raw,StandardCharsets.UTF_8).replace("10000","20000").getBytes(StandardCharsets.UTF_8);assertThrows(ResponseStatusException.class,()->service.webhook(changed,timestamp,sign(changed,timestamp)));
        assertThrows(Exception.class,()->db.update("DELETE FROM sepay_webhook_events WHERE event_key=?",key));
    }
    @Test void providerSendTestDoesNotCreateFinancialEvidence()throws Exception {int before=db.queryForObject("SELECT count(*) FROM sepay_webhook_events",Integer.class);byte[] body="{\"id\":0}".getBytes(StandardCharsets.UTF_8);String time=Long.toString(Instant.now().getEpochSecond());service.webhook(body,time,sign(body,time));assertEquals(before,db.queryForObject("SELECT count(*) FROM sepay_webhook_events",Integer.class));}
    @Test void referenceMustMatchWholeCode(){assertTrue(SepayDisbursementService.hasCode("TKPABC CMR123 x","CMR123"));assertFalse(SepayDisbursementService.hasCode("CMR1234","CMR123"));}
    @Test void correctingReferenceBeforeSettlementRetainsBothAudits(){service.prepare("Bearer admin",id);var proof=proof();client.proof=List.of(proof);service.confirm("Bearer admin",id,confirmBody("TYPO"+UUID.randomUUID()));assertEquals(10000,amount("reserved_balance"));service.confirm("Bearer admin",id,confirmBody(proof.get("reference_number").toString()));assertEquals("PAID",service.detail("Bearer admin",id).get("status"));assertEquals(2,db.queryForObject("SELECT count(*) FROM manual_transfer_confirmations WHERE payout_id=(SELECT id FROM withdrawal_payouts WHERE withdrawal_id=?)",Integer.class,id));assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,confirmBody("CHANGE")));}
    @Test void automaticReferenceUsesVerifiedProviderCodeAndGeneratesRecipientAudit(){
        service.prepare("Bearer admin",id);var proof=proof();client.proof=List.of(proof);service.refresh("Bearer admin",id);
        var observed=service.detail("Bearer admin",id);assertEquals(true,observed.get("bankProofVerified"));assertEquals(proof.get("reference_number"),observed.get("observedReference"));
        service.confirm("Bearer admin",id,Map.of("recipientChecked",true,"useObservedReference",true,"bankReference","UNTRUSTED-CLIENT-CODE"));
        var paid=service.detail("Bearer admin",id);assertEquals("PAID",paid.get("status"));assertEquals(proof.get("reference_number"),paid.get("bankReference"));assertTrue(paid.get("recipientNote").toString().contains("NGUYEN VAN A"));
        assertEquals(0,amount("reserved_balance"));assertEquals(10000,amount("withdrawn_amount"));
    }
    @Test void automaticReferenceStillRequiresExplicitRecipientAttestation(){
        service.prepare("Bearer admin",id);client.proof=List.of(proof());service.refresh("Bearer admin",id);
        assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,Map.of("recipientChecked",false,"useObservedReference",true)));
        assertNull(service.detail("Bearer admin",id).get("confirmedAt"));assertEquals(10000,amount("reserved_balance"));assertEquals(0,amount("withdrawn_amount"));
    }
    @Test void automaticReferenceRejectsMissingAmbiguousAndMismatchedProof(){
        service.prepare("Bearer admin",id);var good=proof();
        var body=Map.<String,Object>of("recipientChecked",true,"useObservedReference",true);
        assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,body));
        for(var mismatch:List.of(Map.of("amount_out",9999L),Map.of("amount_in",1L),Map.of("transfer_type","in"),Map.of("account_number","different"),Map.of("bank_account_id",UUID.randomUUID().toString()))){
            var changed=new LinkedHashMap<String,Object>(good);changed.putAll(mismatch);client.proof=List.of(changed);
            assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,body));
            assertEquals(false,service.detail("Bearer admin",id).get("bankProofVerified"));
        }
        client.proof=List.of(good,new LinkedHashMap<>(good));assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,body));
        assertEquals("",service.detail("Bearer admin",id).get("observedReference"));assertNull(service.detail("Bearer admin",id).get("confirmedAt"));assertEquals(10000,amount("reserved_balance"));
    }
    @Test void automaticReferenceRequiresFreshLookupEvenIfCachedProofWasValid(){
        service.prepare("Bearer admin",id);client.proof=List.of(proof());service.refresh("Bearer admin",id);client.down=true;
        assertThrows(ResponseStatusException.class,()->service.confirm("Bearer admin",id,Map.of("recipientChecked",true,"useObservedReference",true)));
        assertNull(service.detail("Bearer admin",id).get("confirmedAt"));assertEquals(10000,amount("reserved_balance"));
    }
    @Test void concurrentAutomaticConfirmationSettlesExactlyOnce()throws Exception {
        service.prepare("Bearer admin",id);client.proof=List.of(proof());var body=Map.<String,Object>of("recipientChecked",true,"useObservedReference",true);
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.confirm("Bearer admin",id,body));var b=pool.submit(()->service.confirm("Bearer admin",id,body));a.get();b.get();}
        assertEquals("PAID",service.detail("Bearer admin",id).get("status"));assertEquals(10000,amount("withdrawn_amount"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=? AND transaction_type='WITHDRAWAL'",Integer.class,id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM manual_transfer_confirmations WHERE payout_id=(SELECT id FROM withdrawal_payouts WHERE withdrawal_id=?)",Integer.class,id));
    }

}
