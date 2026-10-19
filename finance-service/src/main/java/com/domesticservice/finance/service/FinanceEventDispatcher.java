package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.finance.repository.FinanceEventDispatcherSqlRepository;

@Component
@EnableScheduling
public class FinanceEventDispatcher {

    private final FinanceEventDispatcherSqlRepository repository;

    private final ObjectMapper json;

    private final ServiceHttpClient identity;

    private final String key;

    private final TransactionTemplate tx;

    public FinanceEventDispatcher(FinanceEventDispatcherSqlRepository repository, ObjectMapper json, PlatformTransactionManager manager, @Value("${clients.identity.base-url}") String url, @Value("${INTERNAL_SERVICE_TOKEN:}") String key) {
        this.repository = repository;
        this.json = json;
        identity = new ServiceHttpClient(url);
        this.key = key;
        tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        if (key.isBlank())
            return;
        try {
            for (long id : repository.dispatchFindOutboxEvents(Long.class)) tx.executeWithoutResult(state -> {
                var rows = repository.dispatchFindOutboxEventsForUpdate(id);
                if (rows.isEmpty())
                    return;
                var event = rows.getFirst();
                try {
                    var payload = json.readValue(event.get("payload").toString(), new TypeReference<Map<String, Object>>() {
                    });
                    var recipients = new ArrayList<Long>();
                    if ("PAYMENT".equals(event.get("aggregate_type")))
                        recipients.addAll(repository.dispatchFindPayments(Long.class, event.get("aggregate_id")));
                    else if (payload.get("staffId") instanceof Number n)
                        recipients.add(n.longValue());
                    if (!recipients.isEmpty())
                        identity.internalEvent(key, Map.of("eventKey", "finance:" + id, "userIds", recipients, "type", "PAYMENT".equals(event.get("aggregate_type")) ? "PAYMENT_SUCCESS" : "INCOME_UPDATE", "title", "Cập nhật tài chính", "content", Objects.toString(payload.get("message"), "Thanh toán đã được Finance ghi nhận."), "referenceId", event.get("aggregate_id"), "data", payload));
                    repository.dispatchUpdateOutboxEvents(id);
                } catch (RuntimeException error) {
                    repository.dispatchUpdateOutboxEvents2(id);
                }
            });
        } catch (RuntimeException ignored) {
            /* Retry persisted events after service recovery. */
        }
    }
}
