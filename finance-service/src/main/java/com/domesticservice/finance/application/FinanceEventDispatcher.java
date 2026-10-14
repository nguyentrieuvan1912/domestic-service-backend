package com.domesticservice.finance.application;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
@Component @EnableScheduling
public class FinanceEventDispatcher {
    private final JdbcTemplate db; private final ObjectMapper json; private final ServiceHttpClient identity; private final String key; private final TransactionTemplate tx;
    public FinanceEventDispatcher(JdbcTemplate db,ObjectMapper json,PlatformTransactionManager manager,@Value("${clients.identity.base-url}")String url,@Value("${INTERNAL_SERVICE_TOKEN:}")String key){this.db=db;this.json=json;identity=new ServiceHttpClient(url);this.key=key;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=5000,initialDelay=10000)public void dispatch(){
        if(key.isBlank())return;
        try { for(long id:db.queryForList("SELECT id FROM outbox_events WHERE status IN ('PENDING','FAILED') AND attempts<20 ORDER BY id LIMIT 20",Long.class)) tx.executeWithoutResult(state->{
            var rows=db.queryForList("SELECT * FROM outbox_events WHERE id=? AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED",id); if(rows.isEmpty())return;
            var event=rows.getFirst();
            try {
                var payload=json.readValue(event.get("payload").toString(),new TypeReference<Map<String,Object>>(){});
                var recipients=new ArrayList<Long>();
                if("PAYMENT".equals(event.get("aggregate_type"))) recipients.addAll(db.queryForList("SELECT customer_id FROM payments WHERE id=?",Long.class,event.get("aggregate_id")));
                else if(payload.get("staffId") instanceof Number n) recipients.add(n.longValue());
                if(!recipients.isEmpty()) identity.internalEvent(key,Map.of("eventKey","finance:"+id,"userIds",recipients,"type","PAYMENT".equals(event.get("aggregate_type"))?"PAYMENT_SUCCESS":"INCOME_UPDATE","title","Cập nhật tài chính","content",Objects.toString(payload.get("message"),"Thanh toán đã được Finance ghi nhận."),"referenceId",event.get("aggregate_id"),"data",payload));
                db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?",id);
            } catch(RuntimeException error) { db.update("UPDATE outbox_events SET status='FAILED',attempts=attempts+1 WHERE id=?",id); }
        }); } catch(RuntimeException ignored){ /* Retry persisted events after service recovery. */ }
    }
}
