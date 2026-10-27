package com.domesticservice.finance.repository;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SepayDisbursementRepository {
    private final NativeQueryExecutor db;
    public SepayDisbursementRepository(NativeQueryExecutor db){this.db=db;}
    public void create(long id,long admin,String reference,String account,String bankId,String content) {
        db.insert("INSERT INTO withdrawal_payouts(withdrawal_id,reference_id,idempotency_key,status,initiated_by,provider,source_account,source_bank_id,transfer_content,message) VALUES (?,?,?,'PROCESSING',?,'SEPAY',?,?,?,?)",
            id,reference,UUID.randomUUID().toString(),admin,account,bankId,content,"Đã khóa yêu cầu cho admin chuyển khoản TPBank. Chưa xác nhận tiền ra.");
        db.update("UPDATE withdrawals SET status='PROCESSING' WHERE id=?",id);
    }
    public void confirm(long payout,long admin,String reference,String note) {
        db.insert("INSERT INTO manual_transfer_confirmations(payout_id,admin_id,bank_reference,recipient_note) VALUES (?,?,?,?)",payout,admin,reference,note);
        db.update("UPDATE withdrawal_payouts SET confirmed_by=?,confirmed_at=now(),bank_reference=?,recipient_note=?,message='Admin đã kiểm tra người nhận; chờ xác minh tiền ra qua API SePay.' WHERE id=?",
            admin,reference,note,payout);
    }
    public List<Long> pending(){return db.queryForList("SELECT withdrawal_id FROM withdrawal_payouts WHERE provider='SEPAY' AND status IN ('PROCESSING','REVIEW') ORDER BY checked_at NULLS FIRST,id LIMIT 10",Long.class);}
    public void defer(long withdrawal){db.update("UPDATE withdrawal_payouts SET checked_at=now(),message='Chưa đọc được bằng chứng SePay. Giữ tiền để đối soát; không chuyển lại.' WHERE withdrawal_id=? AND provider='SEPAY' AND status IN ('PROCESSING','REVIEW')",withdrawal);}
    public void observation(long id,String state,String proof,String message,String transaction) {
        db.update("UPDATE withdrawal_payouts SET status=?,checked_at=now(),provider_proof=CAST(? AS jsonb),message=?,bank_transaction_id=coalesce(bank_transaction_id,?) WHERE id=?",
            state,proof,message,transaction,id);
    }
    public void webhook(String key,String hash,String body) {
        var old=db.queryForList("SELECT body_hash FROM sepay_webhook_events WHERE event_key=?",key);
        if(!old.isEmpty() && !hash.equals(old.getFirst().get("body_hash")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Nội dung webhook thay đổi trên cùng mã giao dịch.");
        db.update("INSERT INTO sepay_webhook_events(event_key,body_hash,payload) VALUES (?,?,CAST(? AS jsonb)) ON CONFLICT(event_key) DO NOTHING",key,hash,body);
        // The insert may have raced another arrival; check its immutable content too.
        var stored=db.queryForMap("SELECT body_hash FROM sepay_webhook_events WHERE event_key=?",key);
        if(!hash.equals(stored.get("body_hash")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Webhook trùng mã nhưng khác nội dung.");
    }
    public List<Map<String,Object>> recentEvents(){return db.queryForList("SELECT event_key AS \"eventKey\",received_at AS \"receivedAt\",payload->>'transferType' AS direction,payload->>'transferAmount' AS amount,payload->>'referenceCode' AS reference FROM sepay_webhook_events ORDER BY id DESC LIMIT 10");}
    public List<Map<String,Object>> confirmations(long payout){return db.queryForList("SELECT admin_id AS \"adminId\",bank_reference AS reference,recipient_note AS note,created_at AS \"createdAt\" FROM manual_transfer_confirmations WHERE payout_id=? ORDER BY id",payout);}
}
