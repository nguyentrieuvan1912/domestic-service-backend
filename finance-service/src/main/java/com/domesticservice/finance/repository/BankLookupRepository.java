package com.domesticservice.finance.repository;

import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class BankLookupRepository {
    private final NativeQueryExecutor db;
    public BankLookupRepository(NativeQueryExecutor db){this.db=db;}
    public Map<String,Object> recent(long staff,String bin,String account,String provider){
        var rows=db.queryForList("SELECT * FROM bank_lookup_proofs WHERE staff_id=? AND bank_code=? AND account_number=? AND provider=? AND checked_at>now()-interval '15 minutes' ORDER BY id DESC LIMIT 1",staff,bin,account,provider);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void reserve(long staff){
        // Pay2S's ten calls/minute are shared by the entire integration, not by IP or staff.
        db.execute("SELECT pg_advisory_xact_lock(62910291)");
        if(db.queryForObject("SELECT count(*) FROM bank_lookup_attempts WHERE requested_at>now()-interval '60 seconds'",Long.class)>=10)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đã đạt giới hạn tra tên. Vui lòng thử lại sau một phút.");
        db.insert("INSERT INTO bank_lookup_attempts(staff_id) VALUES (?)",staff);
    }
    public Map<String,Object> proof(long staff,String bin,String account,String name,String provider,String proof){
        long id=db.insert("INSERT INTO bank_lookup_proofs(staff_id,bank_code,account_number,account_holder,provider,provider_proof) VALUES (?,?,?,?,?,CAST(? AS jsonb))",staff,bin,account,name,provider,proof);
        return db.queryForMap("SELECT * FROM bank_lookup_proofs WHERE id=?",id);
    }
    public void verify(long bank,long staff,Map<String,Object> proof){
        int updated=db.update("UPDATE staff_bank_accounts SET account_holder=?,status='VERIFIED',verified_by=NULL,verified_at=now(),verification_provider=?,verification_note=? WHERE id=? AND staff_id=? AND bank_code=? AND account_number=?",
            proof.get("account_holder"),proof.get("provider"),"Tên người nhận được tra cứu qua API "+proof.get("provider")+".",bank,staff,proof.get("bank_code"),proof.get("account_number"));
        if(updated!=1)throw new IllegalStateException("Bank proof does not match account owner");
        db.insert("INSERT INTO bank_provider_verifications(bank_account_id,proof_id) VALUES (?,?)",bank,proof.get("id"));
    }
}
