package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.config.*;
import com.domesticservice.finance.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableConfigurationProperties(SepayProperties.class)
public class SepayDisbursementService {
    public static final String WEBHOOK_PATH="/api/v1/finance/webhooks/sepay";
    private final SepayProperties config;
    private final PayoutProperties limits;
    private final SepayClient client;
    private final WithdrawalPayoutRepository payouts;
    private final SepayDisbursementRepository repo;
    private final PayoutBankDirectory banks;
    private final ServiceHttpClient identity;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public SepayDisbursementService(SepayProperties config,PayoutProperties limits,SepayClient client,WithdrawalPayoutRepository payouts,
        SepayDisbursementRepository repo,PayoutBankDirectory banks,ObjectMapper json,PlatformTransactionManager manager,
        @Value("${clients.identity.base-url}") String identityUrl) {
        this.config=config;this.limits=limits;this.client=client;this.payouts=payouts;this.repo=repo;this.banks=banks;this.json=json;
        tx=new TransactionTemplate(manager);identity=new ServiceHttpClient(identityUrl);
    }
    public boolean enabled(){return config.enabled();}
    public Map<String,Object> capabilities(){return Map.of("mode","SEPAY_MANUAL","configured",config.ready(),"minimum",limits.minimum(),"maximum",limits.maximum(),
        "requiresAdminApproval",true,"automaticNameLookup",false,"message",config.ready()?"Admin chuyển khoản TPBank, kiểm tra người nhận và đối soát tiền ra qua SePay.":"Đã chọn rút qua TPBank/SePay. Chờ bổ sung cấu hình kết nối; chưa thể mở QR chi tiền.");}
    public Map<String,Object> connection(String token) {
        identity.authenticate(token,"ADMIN");var bank=client.source();
        String account=config.sourceAccount(),url=Objects.toString(config.publicUrl(),"").replaceAll("/$","");
        return Map.of("verified",true,"mode","SEPAY_MANUAL","accountName",config.sourceName(),"accountLast4",account.substring(Math.max(0,account.length()-4)),
            "balance",Objects.toString(bank.get("accumulated"),""),"balanceIsLive",false,"webhookUrl",url.startsWith("https://")?url+WEBHOOK_PATH:"",
            "recentEvents",repo.recentEvents(),"message","Đã xác minh tài khoản TPBank tại SePay. Số dư là dữ liệu đồng bộ, kiểm tra app TPBank trước khi chuyển.");
    }
    private long admin(String token){return ServiceHttpClient.userId(identity.authenticate(token,"ADMIN"));}
    private Map<String,Object> withdrawal(long id){var w=payouts.withdrawal(id);if(w==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút.");return w;}
    private Map<String,Object> snapshot(Map<String,Object> w){return json.readValue(w.get("bank_account_snapshot").toString(),new TypeReference<Map<String,Object>>(){});}
    private static long n(Map<String,Object> row,String key){return WithdrawalPayoutService.n(row,key);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private void owner(Map<String,Object> payout,long admin) {
        if(!"SEPAY".equals(payout.get("provider")))throw conflict("Yêu cầu đang thuộc kênh chi khác. Không tạo thêm chuyển khoản.");
        if(n(payout,"initiated_by")!=admin)throw conflict("Yêu cầu đang do admin khác phụ trách chuyển tiền. Không chuyển thêm.");
    }
    public Map<String,Object> prepare(String token,long id) {
        long admin=admin(token);client.requireReady();
        var initial=withdrawal(id);var existing=payouts.find(id);
        if(existing!=null){owner(existing,admin);return view(id,admin);}
        client.source();var bank=snapshot(initial);
        if(!banks.supports(Objects.toString(bank.get("bank_code"),"")))throw conflict("Ngân hàng nhận chưa hỗ trợ QR chuyển khoản.");
        tx.executeWithoutResult(state->{
            payouts.lockWallet(n(initial,"staff_id"));var w=payouts.lockWithdrawal(id);var old=payouts.find(id);
            if(old!=null){owner(old,admin);return;}
            if(!"APPROVED".equals(w.get("status")))throw conflict("Cần duyệt yêu cầu rút trước khi mở QR.");
            if(n(w,"amount")<limits.minimum() || n(w,"amount")>limits.maximum())throw conflict("Số tiền ngoài hạn mức rút.");
            var current=payouts.bank(n(w,"bank_account_id"));
            if(!"VERIFIED".equals(current.get("status")) || current.get("verified_at")==null || Objects.toString(current.get("verification_note"),"").isBlank()
                || !Objects.equals(current.get("staff_id"),w.get("staff_id")) || !Objects.equals(current.get("bank_code"),bank.get("bank_code"))
                || !Objects.equals(current.get("account_number"),bank.get("account_number")) || !Objects.equals(current.get("account_holder"),bank.get("account_holder")))
                throw conflict("Tài khoản thụ hưởng cần được xác minh lại.");
            String ref="CMR"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);
            String content=config.tkp()==null || config.tkp().isBlank()?ref:config.tkp()+" "+ref;
            repo.create(id,admin,ref,config.sourceAccount(),config.bankAccountId(),content);
        });
        return view(id,admin);
    }
    public Map<String,Object> detail(String token,long id){return view(id,admin(token));}
    private Map<String,Object> view(long id,long admin) {
        var w=withdrawal(id);var p=payouts.find(id);if(p==null || !"SEPAY".equals(p.get("provider")))throw conflict("Chưa mở yêu cầu chuyển khoản TPBank.");
        var bank=snapshot(w);var result=new LinkedHashMap<String,Object>();
        result.put("withdrawalId",id);result.put("status",w.get("status"));result.put("payoutStatus",p.get("status"));result.put("message",p.get("message"));
        result.put("initiatedBy",p.get("initiated_by"));
        result.put("bankName",banks.displayName(Objects.toString(bank.get("bank_code"),"")));
        result.put("amount",w.get("amount"));result.put("bankBin",bank.get("bank_code"));result.put("accountNumber",bank.get("account_number"));result.put("accountHolder",bank.get("account_holder"));
        result.put("reference",p.get("reference_id"));result.put("content",p.get("transfer_content"));result.put("sourceLast4",Objects.toString(p.get("source_account"),"").replaceAll("^.*(?=.{4}$)",""));
        boolean observed=false,verified=false;String observedReference="";
        if(p.get("provider_proof")!=null){
            var proof=json.readValue(p.get("provider_proof").toString(),new TypeReference<Map<String,Object>>(){});
            observed="out".equals(proof.get("transfer_type"));
            verified=validProof(p,w,proof) && Objects.equals(p.get("source_bank_id"),config.bankAccountId()) && Objects.equals(p.get("source_account"),config.sourceAccount());
            if(verified)observedReference=proof.get("reference_number").toString();
        }
        result.put("bankProofVerified",verified);result.put("observedReference",observedReference);
        result.put("canTransfer",n(p,"initiated_by")==admin && p.get("confirmed_at")==null && "PROCESSING".equals(p.get("status")) && !observed && config.ready());
        result.put("canConfirm",n(p,"initiated_by")==admin && List.of("PROCESSING","REVIEW").contains(p.get("status")) && config.ready());
        result.put("confirmedAt",p.get("confirmed_at"));result.put("confirmedBy",p.get("confirmed_by"));result.put("bankReference",p.get("bank_reference"));
        result.put("checkedAt",p.get("checked_at"));result.put("recipientNote",p.get("recipient_note"));
        result.put("confirmations",repo.confirmations(n(p,"id")));
        if(Boolean.TRUE.equals(result.get("canTransfer")))result.put("qrCode",BankTransferQr.create(bank.get("bank_code").toString(),bank.get("account_number").toString(),n(w,"amount"),p.get("transfer_content").toString()));
        return result;
    }
    public Map<String,Object> confirm(String token,long id,Map<String,Object> body) {
        long admin=admin(token);client.requireReady();
        if(!Boolean.TRUE.equals(body.get("recipientChecked")))throw conflict("Cần kiểm tra đúng ngân hàng, số tài khoản và tên thụ hưởng trên app TPBank.");
        boolean automatic=Boolean.TRUE.equals(body.get("useObservedReference"));
        String manualReference=Objects.toString(body.get("bankReference"),"").strip(),inputNote=Objects.toString(body.get("recipientNote"),"").strip();
        if(inputNote.length()>2000 || (!automatic && (!manualReference.matches("[A-Za-z0-9._/-]{3,160}") || inputNote.length()<8)))
            throw conflict("Kiểm tra mã giao dịch và ghi chú đối chiếu người nhận (8–2000 ký tự khi nhập thủ công).");
        // Automatic reference selection requires a fresh, authenticated provider lookup, never a client-supplied code or webhook.
        if(automatic)refreshInternal(id);
        var initial=withdrawal(id);
        tx.executeWithoutResult(state->{
            payouts.lockWallet(n(initial,"staff_id"));var w=payouts.lockWithdrawal(id);var original=payouts.find(id);if(original==null)throw conflict("Chưa mở QR chuyển khoản.");
            var p=payouts.lock(n(original,"id"));owner(p,admin);
            String ref=manualReference,note=inputNote;
            if(automatic){
                var proof=p.get("provider_proof")==null?Map.<String,Object>of():json.readValue(p.get("provider_proof").toString(),new TypeReference<Map<String,Object>>(){});
                if(!validProof(p,w,proof) || !Objects.equals(p.get("source_bank_id"),config.bankAccountId()) || !Objects.equals(p.get("source_account"),config.sourceAccount()))
                    throw conflict("Chưa có một giao dịch tiền ra khớp trên SePay. Chờ đồng bộ hoặc nhấn kiểm tra lại; không chuyển thêm.");
                ref=proof.get("reference_number").toString();
                if(ref.length()>160)throw conflict("Mã tham chiếu ngân hàng cần được kiểm tra riêng.");
                if(note.isBlank()){
                    var bank=snapshot(w);
                    note="Admin xác nhận đúng người nhận "+bank.get("account_holder")+" · "+bank.get("bank_code")+" · "+bank.get("account_number")+" trên app TPBank; mã giao dịch lấy tự động từ API SePay.";
                } else if(note.length()<8)throw conflict("Ghi chú bổ sung cần ít nhất 8 ký tự hoặc để trống.");
            }
            if(p.get("confirmed_at")!=null && ref.equals(p.get("bank_reference")) && note.equals(p.get("recipient_note")))return;
            if(!List.of("PROCESSING","REVIEW").contains(p.get("status")))throw conflict("Yêu cầu đã kết thúc.");
            repo.confirm(n(p,"id"),admin,ref,note);
        });
        // Confirmation is durable even when provider lookup fails; retries never transfer money.
        try{refreshInternal(id);}catch(ResponseStatusException ignored){}
        return view(id,admin);
    }
    public Map<String,Object> refresh(String token,long id) {
        var user=identity.authenticate(token,"STAFF","ADMIN");var w=withdrawal(id);
        if(!"ADMIN".equals(user.get("role")) && n(w,"staff_id")!=ServiceHttpClient.userId(user))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút của bạn.");
        refreshInternal(id);var p=payouts.find(id);
        return Map.of("id",id,"status",withdrawal(id).get("status"),"payoutStatus",p.get("status"),"message",p.get("message"));
    }
    void refreshInternal(long id) {
        client.requireReady();var w=withdrawal(id);var p=payouts.find(id);
        if(p==null || !"SEPAY".equals(p.get("provider")) || List.of("PAID","FAILED").contains(p.get("status")))return;
        if(!Objects.equals(p.get("source_bank_id"),config.bankAccountId()) || !Objects.equals(p.get("source_account"),config.sourceAccount()))throw conflict("Nguồn chi đã đổi. Giữ giao dịch cũ để đối soát, không chuyển lại.");
        var candidates=client.candidates(p.get("reference_id").toString(),p.get("source_bank_id").toString());
        var exact=candidates.stream().filter(row->hasCode(Objects.toString(row.get("transaction_content"),""),p.get("reference_id").toString())).toList();
        Map<String,Object> proof=exact.size()==1?client.transaction(Objects.toString(exact.getFirst().get("id"),"")):Map.of("matches",exact.size());
        reconcile(w,p,proof,exact.size());
    }
    static boolean hasCode(String text,String code){return java.util.regex.Pattern.compile("(?<![A-Z0-9])"+java.util.regex.Pattern.quote(code)+"(?![A-Z0-9])").matcher(text.toUpperCase(Locale.ROOT)).find();}
    private void reconcile(Map<String,Object> initial,Map<String,Object> original,Map<String,Object> proof,int count) {
        tx.executeWithoutResult(state->{
            payouts.lockWallet(n(initial,"staff_id"));var w=payouts.lockWithdrawal(n(initial,"id"));var p=payouts.lock(n(original,"id"));
            if("PAID".equals(p.get("status")))return;
            boolean valid=count==1 && validProof(p,w,proof);
            String next="REVIEW",message="Dữ liệu tiền ra chưa khớp hoặc có nhiều giao dịch. Giữ tiền để kiểm tra; không chuyển lại.";
            if(count==0){next="PROCESSING";message="Chưa thấy giao dịch tiền ra khớp trên SePay. Chờ đồng bộ; không chuyển lại nếu đã chuyển.";}
            else if(valid && p.get("confirmed_at")==null){next="PROCESSING";message="SePay đã ghi nhận tiền ra. Mã ngân hàng đã được lấy tự động. Chờ admin xác nhận đúng người thụ hưởng trên app TPBank.";}
            else if(valid && Objects.equals(p.get("bank_reference"),proof.get("reference_number"))) {
                next="PAID";message="Đã đối soát tiền ra SePay và xác nhận người thụ hưởng bởi admin.";
            }
            String transaction="PAID".equals(next)?proof.get("id").toString():null;
            repo.observation(n(p,"id"),next,json.writeValueAsString(proof),message,transaction);
            if("PAID".equals(next))payouts.complete(w,p,"sepay:"+proof.get("reference_number"));
        });
    }
    private boolean validProof(Map<String,Object> p,Map<String,Object> w,Map<String,Object> proof) {
        try {
            var time=LocalDateTime.parse(proof.get("transaction_date").toString(),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            Instant start=p.get("attempted_at") instanceof java.sql.Timestamp stamp?stamp.toInstant():((java.time.OffsetDateTime)p.get("attempted_at")).toInstant();
            return Objects.toString(proof.get("id"),"").matches("[0-9a-fA-F-]{36}") && "out".equals(proof.get("transfer_type"))
                && "TPBANK".equalsIgnoreCase(Objects.toString(proof.get("bank_brand_name"),""))
                && Objects.equals(p.get("source_bank_id"),proof.get("bank_account_id")) && Objects.equals(p.get("source_account"),proof.get("account_number"))
                && n(proof,"amount_out")==n(w,"amount") && n(proof,"amount_in")==0
                && hasCode(Objects.toString(proof.get("transaction_content"),""),p.get("reference_id").toString())
                && !Objects.toString(proof.get("reference_number"),"").isBlank() && !time.isBefore(start.minusSeconds(5)) && !time.isAfter(Instant.now().plusSeconds(300));
        }catch(RuntimeException ex){return false;}
    }
    public void webhook(byte[] raw,String timestamp,String signature) {
        client.requireReady();if(raw.length>65536)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Webhook quá lớn.");
        try {
            long seconds=Long.parseLong(timestamp);
            if(Math.abs(Instant.now().getEpochSecond()-seconds)>300 || signature==null || !signature.matches("sha256=[0-9a-f]{64}"))throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(config.webhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));
            if(!MessageDigest.isEqual(mac.doFinal(raw),HexFormat.of().parseHex(signature.substring(7))))throw new IllegalArgumentException();
        }catch(GeneralSecurityException | RuntimeException ex){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Chữ ký webhook SePay không hợp lệ hoặc đã hết hạn.");}
        String body=new String(raw,StandardCharsets.UTF_8);var event=json.readValue(body,new TypeReference<Map<String,Object>>(){});
        // Provider's Send test uses id=0. Acknowledge it without creating financial evidence.
        if(event.get("id") instanceof Number number && number.longValue()==0)return;
        String key=Objects.toString(event.get("id"),"");if(!key.matches("[A-Za-z0-9-]{1,80}"))throw conflict("Mã giao dịch webhook không hợp lệ.");
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            tx.executeWithoutResult(state->repo.webhook(key,hash,body));
        }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
        // Webhook never settles money. Scheduled authenticated API lookups are authoritative.
    }
    @Scheduled(fixedDelay=30000,initialDelay=20000)
    public void pending(){if(config.ready())for(long id:repo.pending())try{refreshInternal(id);}catch(RuntimeException ignored){tx.executeWithoutResult(state->repo.defer(id));}}
}
