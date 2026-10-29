package com.domesticservice.finance.repository;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class WithdrawalPayoutRepository {
    private final NativeQueryExecutor db;
    public WithdrawalPayoutRepository(NativeQueryExecutor db){this.db=db;}
    public Map<String,Object> withdrawal(long id){var rows=db.queryForList("SELECT * FROM withdrawals WHERE id=?",id);return rows.isEmpty()?null:rows.getFirst();}
    public void lockWallet(long staff){db.queryForMap("SELECT id FROM staff_balances WHERE staff_id=? FOR UPDATE",staff);}
    public Map<String,Object> lockWithdrawal(long id){return db.queryForMap("SELECT * FROM withdrawals WHERE id=? FOR UPDATE",id);}
    public Map<String,Object> bank(long id){return db.queryForMap("SELECT * FROM staff_bank_accounts WHERE id=? FOR SHARE",id);}
    public Map<String,Object> find(long withdrawal){var rows=db.queryForList("SELECT * FROM withdrawal_payouts WHERE withdrawal_id=?",withdrawal);return rows.isEmpty()?null:rows.getFirst();}
    public Map<String,Object> lock(long id){return db.queryForMap("SELECT * FROM withdrawal_payouts WHERE id=? FOR UPDATE",id);}
    public Map<String,Object> intent(long withdrawal,long admin) {
        db.insert("INSERT INTO withdrawal_payouts(withdrawal_id,reference_id,idempotency_key,status,initiated_by) VALUES (?,?,?,'SUBMITTING',?)",
            withdrawal,"CMW-"+UUID.randomUUID(),UUID.randomUUID().toString(),admin);
        db.update("UPDATE withdrawals SET status='PROCESSING' WHERE id=?",withdrawal);return find(withdrawal);
    }
    public void observation(long id,String status,String provider,String proof,String message) {
        db.update("UPDATE withdrawal_payouts SET status=?,provider_id=coalesce(provider_id,?),provider_proof=CAST(? AS jsonb),message=?,checked_at=now() WHERE id=?",status,provider,proof,message,id);
    }
    public void uncertain(long id) {
        db.update("UPDATE withdrawal_payouts SET status='REVIEW',message='Chưa xác minh được kết quả gửi. Giữ tiền và tra cứu cùng mã, không gửi lệnh mới.' WHERE id=? AND status NOT IN ('PAID','FAILED')",id);
    }
    public List<Long> pending(){return db.queryForList("SELECT withdrawal_id FROM withdrawal_payouts WHERE provider='PAYOS' AND status IN ('SUBMITTING','PROCESSING','REVIEW') ORDER BY checked_at NULLS FIRST,id LIMIT 20",Long.class);}
    public void complete(Map<String,Object> withdrawal,Map<String,Object> payout,String reference) {
        Object id=withdrawal.get("id"),wallet=withdrawal.get("balance_id"),amount=withdrawal.get("amount");
        db.insert("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,'DEBIT','RESERVED','WITHDRAWAL',?,?,?)",wallet,amount,id,"withdraw:"+id+":paid");
        db.update("UPDATE staff_balances SET reserved_balance=reserved_balance-?,withdrawn_amount=withdrawn_amount+?,version=version+1,updated_at=now() WHERE id=?",amount,amount,wallet);
        db.update("UPDATE withdrawals SET status='PAID',transfer_reference=?,paid_at=now() WHERE id=?",reference,id);
        db.update("INSERT INTO payout_money_entries(payout_id,account,direction,amount) VALUES (?,'STAFF_PAYABLE','DEBIT',?),(?,'BANK_DISBURSEMENTS','CREDIT',?)",payout.get("id"),amount,payout.get("id"),amount);
        db.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('STAFF',?,'WITHDRAWAL_PAID',jsonb_build_object('message','Đã xác minh chuyển tiền rút về ngân hàng.','referenceId',CAST(? AS bigint)))",withdrawal.get("staff_id"),id);
    }
    public void fail(Map<String,Object> withdrawal) {
        Object id=withdrawal.get("id"),wallet=withdrawal.get("balance_id"),amount=withdrawal.get("amount");
        db.update("INSERT INTO wallet_transactions(balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES (?,'DEBIT','RESERVED','RELEASE',?,?,?),(?,'CREDIT','AVAILABLE','RELEASE',?,?,?)",wallet,amount,id,"withdraw:"+id+":failed-reserved",wallet,amount,id,"withdraw:"+id+":failed-available");
        db.update("UPDATE staff_balances SET reserved_balance=reserved_balance-?,available_balance=available_balance+?,version=version+1,updated_at=now() WHERE id=?",amount,amount,wallet);
        db.update("UPDATE withdrawals SET status='FAILED',rejection_reason='payOS xác nhận giao dịch thất bại; đã hoàn khoản giữ.' WHERE id=?",id);
    }
}
