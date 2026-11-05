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

    public List<Map<String,Object>> reportRecords(Object start,Object end) {
        var records=new ArrayList<Map<String,Object>>();
        records.addAll(db.queryForList("""
            SELECT 'REVENUE' AS kind,id,booking_id AS "bookingId",customer_id AS "actorId",amount,status,
              paid_at AS "effectiveAt",payment_method AS method,COALESCE(transaction_code,provider_reference,'') AS reference,
              COALESCE(admin_note,'') AS description
            FROM payments WHERE status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') AND paid_at>=? AND paid_at<? ORDER BY paid_at DESC,id DESC
            """,start,end));
        records.addAll(db.queryForList("""
            SELECT 'PAYOUT' AS kind,id,NULL AS "bookingId",staff_id AS "actorId",amount,status,
              paid_at AS "effectiveAt",'BANK_TRANSFER' AS method,COALESCE(transfer_reference,'') AS reference,'' AS description
            FROM withdrawals WHERE status='PAID' AND paid_at>=? AND paid_at<? ORDER BY paid_at DESC,id DESC
            """,start,end));
        records.addAll(db.queryForList("""
            SELECT 'REFUND' AS kind,r.id,p.booking_id AS "bookingId",p.customer_id AS "actorId",r.amount,r.status,
              r.completed_at AS "effectiveAt",p.payment_method AS method,COALESCE(r.transaction_code,'') AS reference,COALESCE(r.reason,'') AS description
            FROM refunds r JOIN payments p ON p.id=r.payment_id
            WHERE r.status='PROCESSED' AND r.completed_at>=? AND r.completed_at<? ORDER BY r.completed_at DESC,r.id DESC
            """,start,end));
        records.addAll(db.queryForList("""
            SELECT 'FEE' AS kind,id,booking_id AS "bookingId",staff_id AS "actorId",platform_fee AS amount,status,
              settled_at AS "effectiveAt",'' AS method,'' AS reference,'' AS description,
              service_amount AS "serviceAmount",staff_amount AS "staffAmount",tip_amount AS "tipAmount"
            FROM staff_incomes WHERE status='SETTLED' AND settled_at>=? AND settled_at<? ORDER BY settled_at DESC,id DESC
            """,start,end));
        return records;
    }
}
