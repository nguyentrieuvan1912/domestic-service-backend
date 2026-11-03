package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.repository.BankLookupRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class BankAccountLookupService {
    private final Pay2sBankLookupClient client;
    private final BankLookupRepository repo;
    private final PayoutBankDirectory banks;
    private final ServiceHttpClient identity;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private BankLookupClient alternative;
    private String provider="PAY2S";
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void bankLookup(BankLookupClient client){this.alternative=client;}
    @Value("${payments.bank-lookup.provider:PAY2S}")
    public void provider(String provider){this.provider=provider.strip().toUpperCase(Locale.ROOT);}
    private RecipientNameClient selected(){return switch(provider){case "PAY2S"->client;case "BANKLOOKUP"->alternative;default->null;};}
    public BankAccountLookupService(Pay2sBankLookupClient client,BankLookupRepository repo,PayoutBankDirectory banks,
        @Value("${clients.identity.base-url}") String identityUrl,PlatformTransactionManager manager,ObjectMapper json){
        this.client=client;this.repo=repo;this.banks=banks;this.identity=new ServiceHttpClient(identityUrl);this.tx=new TransactionTemplate(manager);this.json=json;
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean ready(){return selected()!=null && selected().ready();}
    public Map<String,Object> lookup(String token,Map<String,Object> body){
        long staff=ServiceHttpClient.userId(identity.authenticate(token,"STAFF"));
        String bin=FinanceOperationsService.text(body,"bankCode",6,true),account=FinanceOperationsService.text(body,"accountNumber",30,true);
        var proof=resolve(staff,bin,account);
        return Map.of("bankCode",bin,"accountNumber",account,"accountHolder",proof.get("account_holder"),"provider",proof.get("provider"));
    }
    public Map<String,Object> resolve(long staff,String bin,String account){
        if(!bin.matches("[0-9]{6}") || !account.matches("[0-9]{5,30}") || !banks.supports(bin))throw FinanceOperationsService.bad("Chọn ngân hàng được hỗ trợ và nhập đúng số tài khoản.");
        var cached=repo.recent(staff,bin,account,provider);if(cached!=null && ready())return cached;
        if(!ready())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình dịch vụ tra tên tự động. Chưa thể liên kết tài khoản nhận tiền.");
        tx.executeWithoutResult(status->repo.reserve(staff));
        var result=selected().lookup(bin,account);
        return tx.execute(status->repo.proof(staff,bin,account,result.get("accountHolder").toString(),provider,json.writeValueAsString(result.get("proof"))));
    }
    public void verify(long bank,long staff,Map<String,Object> proof){repo.verify(bank,staff,proof);}
}
