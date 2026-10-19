package com.domesticservice.finance.repository;

import java.util.*;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceStatisticsSqlRepository {

    private final NativeQueryExecutor db;

    public FinanceStatisticsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> statsFindPaidAt(Object... args) {
        return db.queryForList("SELECT EXTRACT(MONTH FROM paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM payments WHERE status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') AND paid_at>=? AND paid_at<? GROUP BY 1 ORDER BY 1", args);
    }

    public List<Map<String, Object>> statsFindSettledAt(Object... args) {
        return db.queryForList("SELECT EXTRACT(MONTH FROM settled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(platform_fee) AS amount FROM staff_incomes WHERE status='SETTLED' AND settled_at>=? AND settled_at<? GROUP BY 1 ORDER BY 1", args);
    }

    public List<Map<String, Object>> statsFindPaidAt2(Object... args) {
        return db.queryForList("SELECT EXTRACT(MONTH FROM paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM withdrawals WHERE status='PAID' AND paid_at>=? AND paid_at<? GROUP BY 1 ORDER BY 1", args);
    }

    public List<Map<String, Object>> statsFindCompletedAt(Object... args) {
        return db.queryForList("SELECT EXTRACT(MONTH FROM completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM refunds WHERE status='PROCESSED' AND completed_at>=? AND completed_at<? GROUP BY 1 ORDER BY 1", args);
    }
}
