package com.domesticservice.finance.repository;

import java.util.*;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceEventDispatcherSqlRepository {

    private final NativeQueryExecutor db;

    public FinanceEventDispatcherSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> List<T> dispatchFindOutboxEvents(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM outbox_events WHERE status IN ('PENDING','FAILED') AND attempts<20 ORDER BY id LIMIT 20", type, args);
    }

    public List<Map<String, Object>> dispatchFindOutboxEventsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM outbox_events WHERE id=? AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED", args);
    }

    public <T> List<T> dispatchFindPayments(Class<T> type, Object... args) {
        return db.queryForList("SELECT customer_id FROM payments WHERE id=?", type, args);
    }

    public int dispatchUpdateOutboxEvents(Object... args) {
        return db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?", args);
    }

    public int dispatchUpdateOutboxEvents2(Object... args) {
        return db.update("UPDATE outbox_events SET status='FAILED',attempts=attempts+1 WHERE id=?", args);
    }
}
