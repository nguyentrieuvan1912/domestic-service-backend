package com.domesticservice.finance.repository;

import java.util.*;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceOperationsSqlRepository {

    private final NativeQueryExecutor db;

    public FinanceOperationsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public int balanceInsertStaffBalances(Object... args) {
        return db.update("INSERT INTO staff_balances(staff_id) VALUES (?) ON CONFLICT(staff_id) DO NOTHING", args);
    }

    public List<Map<String, Object>> balanceFindStaffBalancesForUpdate(boolean lock, Object... args) {
        return db.queryForList("SELECT * FROM staff_balances WHERE staff_id=?" + (lock ? " FOR UPDATE" : ""), args);
    }

    public List<Map<String, Object>> walletFindWalletTransactions(Object... args) {
        return db.queryForList("SELECT t.id,t.direction,t.bucket,t.transaction_type AS type,t.amount,t.created_at AS \"createdAt\",t.reference_id AS \"referenceId\" FROM wallet_transactions t JOIN staff_balances b ON b.id=t.balance_id WHERE b.staff_id=? ORDER BY t.created_at DESC,t.id DESC", args);
    }

    public List<Map<String, Object>> walletFindStaffIncomes(Object... args) {
        return db.queryForList("SELECT id,booking_id AS \"bookingId\",working_hours AS \"workingHours\",service_amount AS \"serviceAmount\",platform_fee AS \"platformFee\",staff_amount AS \"staffAmount\",status,income_date AS \"incomeDate\" FROM staff_incomes WHERE staff_id=? ORDER BY id DESC", args);
    }

    public List<Map<String, Object>> walletFindWithdrawals(Object... args) {
        return db.queryForList("SELECT w.id,w.amount,w.status,w.requested_at AS \"requestedAt\",w.rejection_reason AS \"rejectionReason\",w.cancellation_reason AS \"cancellationReason\",w.cancelled_at AS \"cancelledAt\",w.cancelled_by AS \"cancelledBy\",p.provider AS \"payoutProvider\",p.status AS \"payoutStatus\",p.message AS \"payoutMessage\" FROM withdrawals w LEFT JOIN withdrawal_payouts p ON p.withdrawal_id=w.id WHERE w.staff_id=? ORDER BY w.id DESC", args);
    }

    public List<Map<String, Object>> banksFindStaffBankAccounts(Object... args) {
        return db.queryForList("SELECT id,bank_code AS \"bankCode\",account_number AS \"accountNumber\",account_holder AS \"accountHolder\",status,is_default AS \"isDefault\" FROM staff_bank_accounts WHERE staff_id=? AND status<>'INACTIVE' ORDER BY is_default DESC,id", args);
    }

    public <T> List<T> saveBankFindStaffBankAccounts(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM staff_bank_accounts WHERE staff_id=? AND bank_code=? AND account_number=?", type, args);
    }

    public <T> T saveBankFindStaffBankAccounts2(Class<T> type, Object... args) {
        return db.queryForObject("SELECT status FROM staff_bank_accounts WHERE id=?", type, args);
    }

    public <T> T saveBankExistsStaffBankAccounts(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_bank_accounts WHERE staff_id=? AND status<>'INACTIVE')", type, args);
    }

    public long saveBankInsertStaffBankAccounts(Object... args) {
        return insert("INSERT INTO staff_bank_accounts(staff_id,bank_code,account_number,account_holder,is_default) VALUES (?,?,?,?,?)", args);
    }

    public List<Map<String, Object>> withdrawFindWithdrawals(Object... args) {
        return db.queryForList("SELECT id,amount,bank_account_id,status FROM withdrawals WHERE staff_id=? AND request_key=?", args);
    }

    public List<Map<String, Object>> withdrawFindStaffBankAccounts(Object... args) {
        return db.queryForList("SELECT * FROM staff_bank_accounts WHERE id=? AND staff_id=? AND status='VERIFIED' FOR SHARE", args);
    }

    public long withdrawInsertWithdrawals(Object... args) {
        return insert("INSERT INTO withdrawals(staff_id,balance_id,bank_account_id,bank_account_snapshot,amount,status,request_key) VALUES (?,?,?,CAST(? AS jsonb),?,'PENDING',?)", args);
    }

    public int withdrawUpdateStaffBalances(Object... args) {
        return db.update("UPDATE staff_balances SET available_balance=available_balance-?,reserved_balance=reserved_balance+?,version=version+1,updated_at=now() WHERE id=?", args);
    }

    public long eventInsertOutboxEvents(Object... args) {
        return insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('STAFF',?,?,CAST(? AS jsonb))", args);
    }

    public long ledgerInsertWalletTransactions(Object... args) {
        return insert("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,?,?,?,?,?,?)", args);
    }

    public List<Map<String, Object>> adminBanksFindStaffBankAccounts(Object... args) {
        return db.queryForList("SELECT id,staff_id AS \"staffId\",bank_code AS \"bankCode\",account_number AS \"accountNumber\",account_holder AS \"accountHolder\",status,verified_at AS \"verifiedAt\",verified_by AS \"verifiedBy\",(SELECT r.admin_name FROM bank_account_reviews r WHERE r.bank_account_id=staff_bank_accounts.id ORDER BY r.id DESC LIMIT 1) AS \"verifiedByName\",verification_note AS \"verificationNote\" FROM staff_bank_accounts ORDER BY id DESC", args);
    }

    public boolean withdrawalHasPayout(long id){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM withdrawal_payouts WHERE withdrawal_id=?)",Boolean.class,id));}
    public int releaseCancelledWithdrawal(long balance,long staff,long amount){return db.update("UPDATE staff_balances SET reserved_balance=reserved_balance-?,available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=? AND staff_id=? AND reserved_balance>=?",amount,amount,balance,staff,amount);}
    public int cancelPendingWithdrawal(long id,long staff){return db.update("UPDATE withdrawals SET status='CANCELLED',cancelled_by=?,cancelled_at=now(),cancellation_reason='Nhân viên hủy yêu cầu trước khi Admin duyệt.' WHERE id=? AND staff_id=? AND status='PENDING'",staff,id,staff);}

    public List<Map<String,Object>> bankForReview(long id){return db.queryForList("SELECT * FROM staff_bank_accounts WHERE id=? FOR UPDATE",id);}
    public List<Map<String,Object>> bankReviewHistory(long id){return db.queryForList("SELECT id,admin_id AS \"adminId\",admin_name AS \"adminName\",old_status AS \"oldStatus\",new_status AS \"newStatus\",bank_code AS \"bankCode\",account_number AS \"accountNumber\",account_holder AS \"accountHolder\",verification_note AS \"verificationNote\",legacy,reviewed_at AS \"reviewedAt\" FROM bank_account_reviews WHERE bank_account_id=? ORDER BY id DESC",id);}
    public long insertBankReview(Map<String,Object> bank,long admin,String adminName,String status,String note){
        return insert("INSERT INTO bank_account_reviews(bank_account_id,staff_id,admin_id,admin_name,old_status,new_status,bank_code,account_number,account_holder,verification_note) VALUES (?,?,?,?,?,?,?,?,?,?)",
          bank.get("id"),bank.get("staff_id"),admin,adminName,bank.get("status"),status,bank.get("bank_code"),bank.get("account_number"),bank.get("account_holder"),note);
    }

    public int verifyBankUpdateStaffBankAccounts(Object... args) {
        return db.update("UPDATE staff_bank_accounts SET status=?,verification_note=?,verified_by=?,verified_at=now() WHERE id=?", args);
    }

    public List<Map<String, Object>> withdrawalsFindWithdrawals(Object... args) {
        return db.queryForList("SELECT w.id,w.staff_id AS \"staffId\",w.amount,w.status,w.requested_at AS \"requestedAt\",w.approved_at AS \"approvedAt\",w.bank_account_snapshot::text AS \"bankAccount\",w.rejection_reason AS \"rejectionReason\",w.cancellation_reason AS \"cancellationReason\",w.cancelled_at AS \"cancelledAt\",w.cancelled_by AS \"cancelledBy\",p.provider AS \"payoutProvider\",p.status AS \"payoutStatus\",p.message AS \"payoutMessage\" FROM withdrawals w LEFT JOIN withdrawal_payouts p ON p.withdrawal_id=w.id ORDER BY w.id DESC", args);
    }

    public List<Map<String, Object>> reviewWithdrawalFindWithdrawalsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM withdrawals WHERE id=? FOR UPDATE", args);
    }

    public List<Map<String,Object>> reviewWithdrawalFindWithdrawals(Object... args){return db.queryForList("SELECT staff_id FROM withdrawals WHERE id=?",args);}

    public int reviewWithdrawalUpdateStaffBalances(Object... args) {
        return db.update("UPDATE staff_balances SET reserved_balance=reserved_balance-?,available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?", args);
    }

    public int reviewWithdrawalUpdateWithdrawals(Object... args) {
        return db.update("UPDATE withdrawals SET status='REJECTED',rejection_reason=?,approved_by=? WHERE id=?", args);
    }

    public int reviewWithdrawalUpdateWithdrawals2(Object... args) {
        return db.update("UPDATE withdrawals SET status='APPROVED',approved_by=?,approved_at=now() WHERE id=?", args);
    }

    public List<Map<String, Object>> transactionsFindPayments(Object... args) {
        return db.queryForList("SELECT id,booking_id,customer_id,amount,status,payment_method,created_at,admin_note FROM payments ORDER BY id DESC", args);
    }

    public List<Map<String, Object>> transactionsFindWithdrawals(Object... args) {
        return db.queryForList("SELECT id,staff_id,amount,status,requested_at AS created_at,rejection_reason AS admin_note FROM withdrawals ORDER BY id DESC", args);
    }

    public List<Map<String, Object>> transactionsFindRefunds(Object... args) {
        return db.queryForList("SELECT r.id,p.booking_id,p.customer_id,r.amount,r.status,p.payment_method,r.requested_at AS created_at,r.admin_note FROM refunds r JOIN payments p ON p.id=r.payment_id ORDER BY r.id DESC", args);
    }

    public List<Map<String, Object>> noteFindWHEREForUpdate(String table, Object... args) {
        return db.queryForList("SELECT status FROM " + table + " WHERE id=? FOR UPDATE", args);
    }

    public int noteUpdateSET(String table, Object... args) {
        return db.update("UPDATE " + table + " SET admin_note=? WHERE id=?", args);
    }

    public List<Map<String, Object>> invoicesFindInvoices(Map<String, Object> user, Object... args) {
        return db.queryForList("SELECT id,booking_id AS \"bookingId\",invoice_number AS \"invoiceNumber\",subtotal,discount_amount AS \"discountAmount\",total_amount AS \"totalAmount\",status,issued_at AS \"issuedAt\" FROM invoices" + ("CUSTOMER".equals(user.get("role")) ? " WHERE customer_id=?" : "") + " ORDER BY id DESC", args);
    }

    public List<Map<String, Object>> policyFindFinanceSettings(Object... args) {
        return db.queryForList("SELECT name,number_value AS value FROM finance_settings", args);
    }

    public int savePolicyInsertFinanceSettings(Object... args) {
        return db.update("INSERT INTO finance_settings(name,number_value,updated_by) VALUES ('platformFeePercent',?,?) ON CONFLICT(name) DO UPDATE SET number_value=EXCLUDED.number_value,updated_by=EXCLUDED.updated_by,updated_at=now()", args);
    }

    public void cashReceiptLockRecords(long id, Object... args) {
        db.execute("SELECT pg_advisory_xact_lock(" + id + ")");
    }

    public List<Map<String, Object>> cashReceiptFindPayments(Object... args) {
        return db.queryForList("SELECT id,status FROM payments WHERE booking_id=? AND payment_method='CASH' AND status='PAID'", args);
    }

    public long cashReceiptInsertPayments(Object... args) {
        return insert("INSERT INTO payments(booking_id,customer_id,payment_code,amount,payment_method,provider,status,paid_at,admin_note) VALUES (?,?,?,?,'CASH','MANUAL_CASH_RECEIPT','PAID',now(),?)", args);
    }

    public long cashReceiptInsertOutboxEvents(Object... args) {
        return insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('PAYMENT',?,'PAYMENT_CONFIRMED',CAST(? AS jsonb))", args);
    }

    public <T> T paymentStatusFindPayments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(sum(amount),0) FROM payments WHERE booking_id=? AND status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED')", type, args);
    }

    public <T> T paymentStatusFindRefunds(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(sum(r.amount),0) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=? AND r.status='PROCESSED'", type, args);
    }

    public void settleLockRecords(long id, Object... args) {
        db.execute("SELECT pg_advisory_xact_lock(" + id + ")");
    }

    public <T> List<T> settleFindInvoices(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM invoices WHERE booking_id=?", type, args);
    }

    public <T> T settleFindInvoices2(Class<T> type, Object... args) {
        return db.queryForObject("SELECT status FROM invoices WHERE id=?", type, args);
    }

    public <T> T settleFindPayments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(sum(amount),0) FROM payments WHERE booking_id=? AND status='PAID'", type, args);
    }

    public <T> List<T> settleFindFinanceSettings(Class<T> type, Object... args) {
        return db.queryForList("SELECT number_value FROM finance_settings WHERE name='platformFeePercent'", type, args);
    }

    public long settleInsertOrders(Object... args) {
        return insert("INSERT INTO orders(booking_id,order_code,subtotal,add_on_amount,discount_amount,total_amount,payment_method,status,finalized_at) VALUES (?,?,?,?,?,?,?,'FINALIZED',now())", args);
    }

    public long settleInsertOrderDetails(Object... args) {
        return insert("INSERT INTO order_details(order_id,item_type,item_name,quantity,unit_price,total_price) VALUES (?,'PACKAGE',?,1,?,?)", args);
    }

    public long settleInsertOrderDetails2(Object... args) {
        return insert("INSERT INTO order_details(order_id,item_type,item_name,quantity,unit_price,total_price) VALUES (?,'ADD_ON','Các hạng mục bổ sung',1,?,?)", args);
    }

    public long settleInsertInvoices(Object... args) {
        return insert("INSERT INTO invoices(order_id,booking_id,customer_id,invoice_number,customer_snapshot,service_snapshot,subtotal,discount_amount,total_amount,status) VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,'PAID')", args);
    }

    public long settleInsertInvoiceItems(Object... args) {
        return insert("INSERT INTO invoice_items(invoice_id,name,quantity,unit_price,total_price) VALUES (?,?,1,?,?)", args);
    }

    public long settleInsertStaffIncomes(Object... args) {
        return insert("INSERT INTO staff_incomes(staff_id,booking_id,balance_id,working_hours,service_amount,platform_fee,staff_amount,status,income_date,settled_at) VALUES (?,?,?,?,?,?,?,'SETTLED',(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,now())", args);
    }

    public int settleUpdateStaffBalances(Object... args) {
        return db.update("UPDATE staff_balances SET available_balance=available_balance+?,total_earned=total_earned+?,version=version+1,updated_at=now() WHERE id=?", args);
    }

    public int settleUpdatePayments(Object... args) {
        return db.update("UPDATE payments SET order_id=? WHERE booking_id=? AND status='PAID'", args);
    }

    public List<Map<String, Object>> adjustmentsFindRewards(Object... args) {
        return db.queryForList("SELECT * FROM rewards ORDER BY id DESC", args);
    }

    public List<Map<String, Object>> adjustmentsFindPenalties(Object... args) {
        return db.queryForList("SELECT * FROM penalties ORDER BY id DESC", args);
    }

    public <T> T requestRewardFindStaffIncomes(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(sum(staff_amount),0) FROM staff_incomes WHERE staff_id=? AND status='SETTLED' AND income_date>=? AND income_date<=?", type, args);
    }

    public <T> List<T> requestRewardFindRewards(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM rewards WHERE staff_id=? AND reward_type=? AND period_start=? AND period_end=?", type, args);
    }

    public long requestRewardInsertRewards(Object... args) {
        return insert("INSERT INTO rewards(staff_id,reward_type,period_start,period_end,revenue,amount,reason,status) VALUES (?,?,?,?,?,?,?,'PENDING')", args);
    }

    public List<Map<String, Object>> requestPenaltyFindPenalties(Object... args) {
        return db.queryForList("SELECT id,amount,reason FROM penalties WHERE staff_id=? AND request_key=?", args);
    }

    public long requestPenaltyInsertPenalties(Object... args) {
        return insert("INSERT INTO penalties(staff_id,penalty_type,amount,reason,status,request_key) VALUES (?,'ADMIN_REVIEW',?,?,'PENDING',?)", args);
    }

    public List<Map<String, Object>> applyAdjustmentFindWHEREForUpdate(String table, Object... args) {
        return db.queryForList("SELECT * FROM " + table + " WHERE id=? FOR UPDATE", args);
    }

    public int applyAdjustmentUpdateStaffBalances(Object... args) {
        return db.update("UPDATE staff_balances SET available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?", args);
    }

    public int applyAdjustmentUpdateSET(String table, Object... args) {
        return db.update("UPDATE " + table + " SET status='APPLIED' WHERE id=?", args);
    }
}
