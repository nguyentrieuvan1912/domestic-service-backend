package com.domesticservice.finance.repository;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class StaffTopupRepository {
    private final NativeQueryExecutor db;
    public StaffTopupRepository(NativeQueryExecutor db) { this.db = db; }
    public void lockWallet(long staff) {
        db.update("INSERT INTO staff_balances(staff_id) VALUES (?) ON CONFLICT(staff_id) DO NOTHING", staff);
        db.queryForMap("SELECT id FROM staff_balances WHERE staff_id=? FOR UPDATE", staff);
    }
    public Map<String, Object> request(long staff, long amount, String key) {
        lockWallet(staff);
        var old = db.queryForList("SELECT * FROM staff_topups WHERE staff_id=? AND request_key=?", staff, key);
        if (!old.isEmpty()) return old.getFirst();
        long id = db.insert("INSERT INTO staff_topups(staff_id,amount,request_key,description) VALUES (?,?,?,'NAP')", staff, amount, key);
        db.update("UPDATE staff_topups SET description=? WHERE id=?", "NAP" + Long.toString(id,36).toUpperCase(Locale.ROOT), id);
        return find(id, false);
    }
    public Map<String, Object> find(long id, boolean lock) {
        var rows = db.queryForList("SELECT * FROM staff_topups WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<Map<String, Object>> list(Long staff) {
        return db.queryForList("SELECT * FROM staff_topups" + (staff == null ? "" : " WHERE staff_id=?") + " ORDER BY id DESC LIMIT 100", staff == null ? new Object[]{} : new Object[]{staff});
    }
    public void link(long id, Map<String, Object> data) {
        db.update("UPDATE staff_topups SET receiving_account_verified=true,provider_link_id=?,checkout_url=?,qr_content=?,account_number=?,account_name=?,bank_bin=?,status=CASE WHEN status='CREATING' THEN 'PENDING' ELSE status END WHERE id=?",
            data.get("paymentLinkId"), data.get("checkoutUrl"), data.get("qrCode"), data.get("accountNumber"), data.get("accountName"), Objects.toString(data.get("bin"), ""), id);
    }
    public void observation(long id, String status, long received) {
        db.update("UPDATE staff_topups SET status=?,amount_received=?,verified_at=now() WHERE id=?", status, received, id);
    }
    public void credit(long id, long staff, long amount) {
        long wallet = ((Number) db.queryForMap("SELECT id FROM staff_balances WHERE staff_id=?", staff).get("id")).longValue();
        db.insert("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,'CREDIT','AVAILABLE','TOP_UP',?,?,?)", wallet, amount, id, "topup:" + id);
        db.update("UPDATE staff_balances SET available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?", amount, wallet);
        db.update("INSERT INTO topup_money_entries(topup_id,account,direction,amount) VALUES (?,'BANK_RECEIPTS','DEBIT',?),(?,'STAFF_PAYABLE','CREDIT',?)", id, amount, id, amount);
        db.update("UPDATE staff_topups SET status='PAID',paid_at=now() WHERE id=?", id);
        db.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('STAFF',?,'TOPUP_PAID',jsonb_build_object('staffId',CAST(? AS bigint),'referenceId',CAST(? AS bigint),'message','Đã xác minh nạp tiền vào số dư.'))", staff, staff, id);
    }
    public void event(String key, long id, String payload) {
        db.update("INSERT INTO topup_provider_events(event_key,topup_id,payload) VALUES (?,?,CAST(? AS jsonb)) ON CONFLICT(event_key) DO NOTHING", key, id, payload);
    }
    public List<Long> pending() {
        return db.queryForList("SELECT id FROM staff_topups WHERE status IN ('CREATING','PENDING','REVIEW') AND created_at>now()-interval '2 days' ORDER BY verified_at NULLS FIRST,id LIMIT 20", Long.class);
    }
}
