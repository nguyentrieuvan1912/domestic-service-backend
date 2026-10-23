package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.dto.StaffTopupRequest;
import com.domesticservice.finance.repository.StaffTopupRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class StaffTopupService {
    private final StaffTopupRepository repository;
    private final PayosClient provider;
    private final ServiceHttpClient identity;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public StaffTopupService(StaffTopupRepository repository, PayosClient provider, PlatformTransactionManager manager,
                             ObjectMapper json, @Value("${clients.identity.base-url}") String identityUrl) {
        this.repository=repository; this.provider=provider; this.tx=new TransactionTemplate(manager);
        this.identity=new ServiceHttpClient(identityUrl); this.json=json;
    }
    public Map<String, Object> readiness(String token) {
        identity.authenticate(token,"STAFF","ADMIN","CUSTOMER");
        return Map.of("provider","PAYOS","topupEnabled",provider.ready(),"minimumTopup",2000,"maximumTopup",1000000,
            "payoutEnabled",false,"message",provider.ready()?"Đã cấu hình kênh thu tiền. Giao dịch vẫn cần xác minh từ payOS.":"Chưa kết nối kênh thanh toán payOS.");
    }
    public List<Map<String, Object>> list(String token, boolean admin) {
        var user=identity.authenticate(token,admin?"ADMIN":"STAFF");
        return repository.list(admin?null:ServiceHttpClient.userId(user)).stream().map(this::view).toList();
    }
    public Map<String, Object> create(String token, StaffTopupRequest request) {
        long staff=ServiceHttpClient.userId(identity.authenticate(token,"STAFF"));
        provider.requireReady();
        var row=tx.execute(state->repository.request(staff,request.amount(),request.requestKey()));
        if (number(row,"amount")!=request.amount()) throw conflict("Mã yêu cầu đã dùng cho số tiền khác.");
        long id=number(row,"id");
        if (!"CREATING".equals(row.get("status"))) return view(row);
        // The intent is committed before network I/O. A lost response reuses this exact provider order code.
        var existing=provider.lookup(id);
        if(existing!=null) {
            if(number(existing,"orderCode")!=id || number(existing,"amount")!=request.amount()) throw conflict("Kết quả khôi phục không khớp yêu cầu nạp.");
            // GET proof does not contain the receiving account. A lost creation response
            // remains reviewable, but cannot bypass the account validation or create a second intent.
            reconcile(id,existing); return view(repository.find(id,false));
        }
        if (((java.sql.Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now())) {
            tx.executeWithoutResult(state->repository.observation(id,"EXPIRED",0));
            return view(repository.find(id,false));
        }
        var link=provider.create(id,request.amount(),row.get("description").toString(),((java.sql.Timestamp)row.get("expires_at")).toInstant().getEpochSecond());
        if(number(link,"orderCode")!=id || number(link,"amount")!=request.amount() || !provider.receivingAccountMatches(link)
            || !row.get("description").equals(link.get("description")) || !"VND".equals(link.get("currency"))
            || !validCheckout(Objects.toString(link.get("checkoutUrl"),"")) || Objects.toString(link.get("paymentLinkId"),"").isBlank())
            throw conflict("Thông tin tài khoản nhận hoặc mã thanh toán payOS không khớp cấu hình. Chưa được chuyển tiền.");
        tx.executeWithoutResult(state->{repository.lockWallet(staff);repository.find(id,true);repository.link(id,link);});
        return view(repository.find(id,false));
    }
    static boolean validCheckout(String url) {
        try { var uri=java.net.URI.create(url);return "https".equals(uri.getScheme()) && "pay.payos.vn".equals(uri.getHost()) && uri.getUserInfo()==null; }
        catch(IllegalArgumentException e){return false;}
    }
    public Map<String, Object> refresh(String token,long id,boolean admin) {
        var user=identity.authenticate(token,admin?"ADMIN":"STAFF");
        var row=repository.find(id,false);
        if(row==null || !admin && number(row,"staff_id")!=ServiceHttpClient.userId(user)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu nạp của bạn.");
        if(!"PAID".equals(row.get("status"))) {
            var proof=provider.lookup(id);
            if(proof!=null) reconcile(id,proof);
        }
        return view(repository.find(id,false));
    }
    @SuppressWarnings("unchecked")
    public void webhook(Map<String,Object> envelope) {
        provider.requireReady();
        if(!(envelope.get("data") instanceof Map<?,?>)) throw conflict("Dữ liệu webhook không hợp lệ.");
        var data=(Map<String,Object>)envelope.get("data");
        if(!provider.verify(data,Objects.toString(envelope.get("signature"),""))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Chữ ký payOS không hợp lệ.");
        if(!Boolean.TRUE.equals(envelope.get("success")) || !"00".equals(data.get("code"))) return;
        long id=number(data,"orderCode");
        var row=repository.find(id,false);
        // Provider's setup sample and other channel payments must never create a wallet or credit money.
        if(row==null) return;
        if(!Boolean.TRUE.equals(row.get("receiving_account_verified")) || !Objects.equals(row.get("account_number"),data.get("accountNumber")) || !"VND".equals(data.get("currency"))) throw conflict("Tài khoản nhận không khớp yêu cầu nạp.");
        String reference=Objects.toString(data.get("reference"),"");
        if(reference.isBlank() || reference.length()>120) throw conflict("Thiếu mã giao dịch ngân hàng.");
        tx.executeWithoutResult(state->repository.event("payos:"+reference,id,json.writeValueAsString(envelope)));
        var proof=provider.lookup(id);
        if(proof!=null) reconcile(id,proof);
    }
    void reconcile(long id,Map<String,Object> proof) {
        var initial=repository.find(id,false);
        if(initial==null) return;
        tx.executeWithoutResult(state->{
            long staff=number(initial,"staff_id");repository.lockWallet(staff);
            var row=repository.find(id,true);
            if("PAID".equals(row.get("status"))) return;
            long received=number(proof,"amountPaid"),amount=number(row,"amount");
            String link=Objects.toString(proof.get("id"),"");
            if(number(proof,"orderCode")!=id || number(proof,"amount")!=amount || link.isBlank()
                || row.get("provider_link_id")!=null && !row.get("provider_link_id").equals(link)) throw conflict("Kết quả đối soát không khớp yêu cầu nạp.");
            String status=Objects.toString(proof.get("status"),"");
            boolean accountVerified=Boolean.TRUE.equals(row.get("receiving_account_verified"));
            String next=received>0 || !accountVerified?"REVIEW":switch(status){case "CANCELLED"->"CANCELLED";case "EXPIRED"->"EXPIRED";default->"PENDING";};
            repository.observation(id,next,received);
            if(accountVerified && "PAID".equals(status) && received==amount && number(proof,"amountRemaining")==0) repository.credit(id,staff,amount);
        });
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void reconcilePending() {
        if(!provider.ready()) return;
        for(long id:repository.pending()) {
            try {
                var proof=provider.lookup(id);
                if(proof!=null) reconcile(id,proof);
                else tx.executeWithoutResult(state->{
                    var row=repository.find(id,true);
                    if(row!=null && "CREATING".equals(row.get("status"))) repository.observation(id,
                        ((java.sql.Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now())?"EXPIRED":"CREATING",0);
                });
            }
            catch(RuntimeException ignored) { /* Persisted intent remains available for the next reconciliation. */ }
        }
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","amount","status","description")) result.put(key,row.get(key));
        result.put("staffId",row.get("staff_id"));result.put("checkoutUrl",row.get("checkout_url"));
        result.put("requestKey",row.get("request_key"));
        // Expose only the provider-signed QR persisted for this verified, payable intent.
        boolean payable=Boolean.TRUE.equals(row.get("receiving_account_verified")) && "PENDING".equals(row.get("status"))
            && ((java.sql.Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now());
        result.put("qrCode",payable?row.get("qr_content"):null);
        result.put("accountNumber",row.get("account_number"));result.put("accountName",row.get("account_name"));result.put("bankBin",row.get("bank_bin"));
        result.put("amountReceived",row.get("amount_received"));result.put("createdAt",row.get("created_at"));
        result.put("expiresAt",row.get("expires_at"));result.put("paidAt",row.get("paid_at"));result.put("verifiedAt",row.get("verified_at"));
        return result;
    }
    private static long number(Map<String,Object> row,String key) {
        Object value=row.get(key);
        if(!(value instanceof Number n) || n.longValue()<0 || n.doubleValue()!=n.longValue()) throw conflict("Dữ liệu số tiền hoặc mã thanh toán không hợp lệ.");
        return ((Number)value).longValue();
    }
    private static ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
