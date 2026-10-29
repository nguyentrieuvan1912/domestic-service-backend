package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.config.PayoutProperties;
import com.domesticservice.finance.repository.WithdrawalPayoutRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class WithdrawalPayoutService {
    private final WithdrawalPayoutRepository repo;
    private final PayosPayoutClient provider;
    private final PayoutProperties config;
    private final PayoutBankDirectory directory;
    private final ServiceHttpClient identity;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private SepayDisbursementService manual;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void manualDisbursement(SepayDisbursementService manual){this.manual=manual;}
    public WithdrawalPayoutService(WithdrawalPayoutRepository repo,PayosPayoutClient provider,PayoutProperties config,
        PayoutBankDirectory directory,PlatformTransactionManager manager,ObjectMapper json,@Value("${clients.identity.base-url}") String identityUrl) {
        this.repo=repo;this.provider=provider;this.config=config;this.directory=directory;this.tx=new TransactionTemplate(manager);this.json=json;identity=new ServiceHttpClient(identityUrl);
    }
    public Map<String,Object> capabilities(String token) {
        identity.authenticate(token,"STAFF","ADMIN");
        if(manual!=null && manual.enabled())return manual.capabilities();
        return Map.of("configured",config.ready(),"minimum",config.minimum(),"maximum",config.maximum(),"requiresAdminApproval",true,
            "automaticNameLookup",false,"message",config.ready()?"Kênh chi đã được cấu hình; mỗi yêu cầu cần duyệt và xác minh kết quả chuyển tiền.":"Chưa cấu hình đầy đủ Kênh chi payOS. Yêu cầu rút chưa thể chuyển tự động.");
    }
    public List<Map<String,Object>> banks(String token){identity.authenticate(token,"STAFF","ADMIN");return directory.banks();}
    public Map<String,Object> connection(String token) {
        if(manual!=null && manual.enabled())return manual.connection(token);
        identity.authenticate(token,"ADMIN");var source=provider.source();
        String account=source.get("accountNumber").toString();
        return Map.of("verified",true,"currency","VND","balance",source.get("balance"),"accountName",source.get("accountName"),"accountLast4",account.substring(Math.max(0,account.length()-4)));
    }
    public Map<String,Object> dispatch(String token,long id,boolean confirmed) {
        long admin=ServiceHttpClient.userId(identity.authenticate(token,"ADMIN"));
        var previous=repo.find(id);
        if(previous!=null && "SEPAY".equals(previous.get("provider")))throw conflict("Yêu cầu đã khóa cho chuyển khoản TPBank. Không gửi chi payOS.");
        if(previous==null && manual!=null && manual.enabled())throw conflict("Đang dùng TPBank/SePay. Mở QR chuyển khoản tại yêu cầu đã duyệt.");
        if(!confirmed) throw conflict("Cần xác nhận gửi lệnh chi thực tế.");
        provider.requireReady();
        var w=requireWithdrawal(id);
        if(repo.find(id)!=null) return refreshInternal(id);
        var source=provider.source();
        long amount=n(w,"amount");
        if(amount<config.minimum() || amount>config.maximum()) throw conflict("Số tiền ngoài hạn mức Kênh chi cấu hình.");
        try {if(Long.parseLong(source.get("balance").toString())<amount) throw conflict("Nguồn chi Bảo Kim không đủ tiền.");}
        catch(NumberFormatException ex){throw conflict("Số dư nguồn chi không hợp lệ.");}
        var snapshot=bankSnapshot(w);
        if(!directory.supports(Objects.toString(snapshot.get("bank_code"),""))) throw conflict("Ngân hàng nhận chưa được cấu hình hỗ trợ chi.");
        var prepared=tx.execute(state->{
            repo.lockWallet(n(w,"staff_id"));var locked=repo.lockWithdrawal(id);
            var old=repo.find(id);if(old!=null) return Map.of("created",false,"payout",old);
            if(!"APPROVED".equals(locked.get("status"))) throw conflict("Chỉ gửi chi yêu cầu đã được admin duyệt.");
            var bank=repo.bank(n(locked,"bank_account_id"));
            if(!"VERIFIED".equals(bank.get("status")) || bank.get("verified_at")==null || Objects.toString(bank.get("verification_note"),"").isBlank()
                || !Objects.equals(bank.get("staff_id"),locked.get("staff_id"))
                || !Objects.equals(bank.get("bank_code"),snapshot.get("bank_code")) || !Objects.equals(bank.get("account_number"),snapshot.get("account_number"))
                || !Objects.equals(bank.get("account_holder"),snapshot.get("account_holder"))) throw conflict("Tài khoản nhận cần được đối chiếu và xác minh lại trước khi chi.");
            return Map.of("created",true,"payout",repo.intent(id,admin));
        });
        if(!Boolean.TRUE.equals(prepared.get("created"))) return refreshInternal(id);
        // Intent/attempt is durable before HTTP. An ambiguous result is lookup-only, never re-posted.
        var payout=repo.find(id);
        try {
            var result=provider.create(Map.of("referenceId",payout.get("reference_id"),"amount",amount,
                "description","CM rut tien "+id,"toBin",snapshot.get("bank_code"),"toAccountNumber",snapshot.get("account_number")),payout.get("idempotency_key").toString());
            reconcile(id,result);
        }catch(RuntimeException ex){tx.executeWithoutResult(state->repo.uncertain(n(payout,"id")));throw ex;}
        return view(id);
    }
    public Map<String,Object> refresh(String token,long id) {
        var user=identity.authenticate(token,"STAFF","ADMIN");var w=requireWithdrawal(id);
        if(!"ADMIN".equals(user.get("role")) && n(w,"staff_id")!=ServiceHttpClient.userId(user)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút của bạn.");
        var existing=repo.find(id);
        if(existing!=null && "SEPAY".equals(existing.get("provider")))return manual.refresh(token,id);
        return refreshInternal(id);
    }
    private Map<String,Object> refreshInternal(long id) {
        var p=repo.find(id);
        if(p!=null && !"PAYOS".equals(p.get("provider")))throw conflict("Yêu cầu thuộc kênh TPBank/SePay. Không đối soát qua payOS.");
        if(p!=null && !List.of("PAID","FAILED").contains(p.get("status"))) {
            var proof=provider.find(p.get("reference_id").toString());
            if(proof!=null) reconcile(id,proof);
        }
        return view(id);
    }
    private Map<String,Object> view(long id) {
        var w=requireWithdrawal(id);var p=repo.find(id);var result=new LinkedHashMap<String,Object>();
        result.put("id",id);result.put("status",w.get("status"));
        result.put("payoutStatus",p==null?null:p.get("status"));result.put("message",p==null?"Chưa gửi lệnh chi.":p.get("message"));return result;
    }
    private Map<String,Object> requireWithdrawal(long id){var w=repo.withdrawal(id);if(w==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút.");return w;}
    private Map<String,Object> bankSnapshot(Map<String,Object> row){return json.readValue(row.get("bank_account_snapshot").toString(),new TypeReference<Map<String,Object>>(){});}
    @SuppressWarnings("unchecked")
    void reconcile(long id,Map<String,Object> proof) {
        var initial=requireWithdrawal(id);
        tx.executeWithoutResult(state->{
            repo.lockWallet(n(initial,"staff_id"));var w=repo.lockWithdrawal(id);var p=repo.lock(n(repo.find(id),"id"));
            if(List.of("PAID","FAILED").contains(p.get("status"))) return;
            var bank=bankSnapshot(w);String providerId=Objects.toString(proof.get("id"),"");
            boolean match=providerId.matches("[A-Za-z0-9_-]{1,160}") && Objects.equals(p.get("reference_id"),proof.get("referenceId"))
                && (p.get("provider_id")==null || p.get("provider_id").equals(providerId));
            Map<String,Object> item=null;
            if(proof.get("transactions") instanceof List<?> rows && rows.size()==1 && rows.getFirst() instanceof Map<?,?>) item=(Map<String,Object>)rows.getFirst();
            match=match && item!=null && Objects.equals(item.get("referenceId"),p.get("reference_id"))
                && Objects.equals(item.get("toBin"),bank.get("bank_code")) && Objects.equals(item.get("toAccountNumber"),bank.get("account_number"));
            if(match) {try{match=n(item,"amount")==n(w,"amount");}catch(RuntimeException ex){match=false;}}
            String next="REVIEW",message="Thông tin kết quả không khớp; giữ tiền để đối soát.",reference="";
            if(match) {
                String status=Objects.toString(item.get("state"),"");String approval=Objects.toString(proof.get("approvalState"),"");
                reference=Objects.toString(item.get("id"),"");
                if("SUCCEEDED".equals(status) && List.of("COMPLETED","SUCCEEDED").contains(approval)
                    && reference.matches("[A-Za-z0-9_-]{1,140}") && !Objects.toString(item.get("toAccountName"),"").isBlank()
                    && PayosPayoutClient.normalizeName(item.get("toAccountName")).equals(PayosPayoutClient.normalizeName(bank.get("account_holder")))) {
                    next="PAID";message="payOS đã xác nhận chuyển tiền thành công.";
                }else if("FAILED".equals(status) && List.of("FAILED","COMPLETED").contains(approval)) {
                    next="FAILED";message="payOS xác nhận giao dịch thất bại; đã hoàn khoản giữ.";
                }else if(List.of("RECEIVED","PROCESSING","ON_HOLD").contains(status)) {
                    next="PROCESSING";message="Lệnh chi đang xử lý hoặc chờ phê duyệt tại payOS.";
                }
            }
            repo.observation(n(p,"id"),next,match?providerId:null,json.writeValueAsString(proof),message);
            if("PAID".equals(next)) repo.complete(w,p,"payos:"+reference);
            else if("FAILED".equals(next)) repo.fail(w);
        });
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void reconcilePending(){if(config.ready()) for(long id:repo.pending())try{refreshInternal(id);}catch(RuntimeException ignored){/* Durable hold remains; no blind resubmission. */}}
    static long n(Map<String,Object> map,String key){if(!(map.get(key) instanceof Number n) || n.longValue()<0 || n.doubleValue()!=n.longValue())throw conflict("Dữ liệu số tiền không hợp lệ.");return n.longValue();}
    static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
