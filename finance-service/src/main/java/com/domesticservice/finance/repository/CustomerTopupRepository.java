package com.domesticservice.finance.repository;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerTopupRepository {
    private final NativeQueryExecutor db;
    public CustomerTopupRepository(NativeQueryExecutor db) { this.db = db; }
    public void lockWallet(long staff) {
        db.update("INSERT INTO customer_wallets(customer_id) VALUES (?) ON CONFLICT(customer_id) DO NOTHING", staff);
        db.queryForMap("SELECT customer_id FROM customer_wallets WHERE customer_id=? FOR UPDATE", staff);
    }
    public Map<String, Object> request(long staff, long amount, String key, Long bookingId) {
        lockWallet(staff);
        var old = db.queryForList("SELECT * FROM customer_topups WHERE customer_id=? AND (request_key=? OR booking_id=?)", staff, key, bookingId);
        if (!old.isEmpty()) return old.getFirst();
        long id = db.insert("INSERT INTO customer_topups(customer_id,amount,request_key,description,booking_id) VALUES (?,?,?,'NAP',?)", staff, amount, key, bookingId);
        db.update("UPDATE customer_topups SET description=? WHERE id=?", "NAP" + Long.toString(id,36).toUpperCase(Locale.ROOT), id);
        return find(id, false);
    }
    public Map<String, Object> find(long id, boolean lock) {
        var rows = db.queryForList("SELECT * FROM customer_topups WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<Map<String, Object>> list(Long staff) {
        return db.queryForList("SELECT * FROM customer_topups" + (staff == null ? "" : " WHERE customer_id=?") + " ORDER BY id DESC LIMIT 100", staff == null ? new Object[]{} : new Object[]{staff});
    }
    public void link(long id, Map<String, Object> data) {
        db.update("UPDATE customer_topups SET receiving_account_verified=true,provider_link_id=?,checkout_url=?,qr_content=?,account_number=?,account_name=?,bank_bin=?,status=CASE WHEN status='CREATING' THEN 'PENDING' ELSE status END WHERE id=?",
            data.get("paymentLinkId"), data.get("checkoutUrl"), data.get("qrCode"), data.get("accountNumber"), data.get("accountName"), Objects.toString(data.get("bin"), ""), id);
    }
    public void observation(long id, String status, long received) {
        db.update("UPDATE customer_topups SET status=?,amount_received=?,verified_at=now() WHERE id=?", status, received, id);
    }
    public void credit(long id, long staff, long amount) {
        db.insert("INSERT INTO customer_wallet_transactions(customer_id,direction,transaction_type,amount,idempotency_key) VALUES (?,'CREDIT','TOP_UP',?,?)",staff,amount,"customer-topup:"+id);
        db.update("UPDATE customer_wallets SET available_balance=available_balance+?,updated_at=now() WHERE customer_id=?",amount,staff);
        var intent=find(id,false);
        if(intent.get("booking_id") instanceof Number booking){
            long bookingId=booking.longValue(),price=((Number)intent.get("amount")).longValue();
            long payment=db.insert("INSERT INTO payments(booking_id,customer_id,payment_code,amount,payment_method,provider,transaction_code,status,paid_at) VALUES (?,?,?,?,'BANK_TRANSFER_QR','PAYOS',?,'PAID',now())",bookingId,staff,"CUSTOMER-"+bookingId,price,"customer-topup-"+id);
            db.insert("INSERT INTO customer_wallet_transactions(customer_id,direction,transaction_type,amount,booking_id,idempotency_key) VALUES (?,'DEBIT','PAYMENT',?,?,?)",staff,price,bookingId,"booking:"+bookingId+":pay");
            db.update("UPDATE customer_wallets SET available_balance=available_balance-? WHERE customer_id=?",price,staff);
            db.update("INSERT INTO customer_booking_payments(booking_id,customer_id,payment_id,amount) VALUES (?,?,?,?)",bookingId,staff,payment,price);
        }
        db.update("UPDATE customer_topups SET status='PAID',paid_at=now() WHERE id=?",id);
    }
    public void event(String key, long id, String payload) {
        db.update("INSERT INTO customer_topup_events(event_key,topup_id,payload) VALUES (?,?,CAST(? AS jsonb)) ON CONFLICT(event_key) DO NOTHING", key, id, payload);
    }
    public List<Long> pending() {
        return db.queryForList("SELECT id FROM customer_topups WHERE status<>'PAID' ORDER BY verified_at NULLS FIRST,id LIMIT 20", Long.class);
    }
}
