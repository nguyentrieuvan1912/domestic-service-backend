package com.domesticservice.identity.repository;

import java.util.*;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationVerificationSqlRepository {

    private final NativeQueryExecutor db;

    public RegistrationVerificationSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> T sendExistsUsers(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(email)=?)", type, args);
    }

    public List<Map<String, Object>> sendFindRegistrationVerificationsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM registration_verifications WHERE email=? FOR UPDATE", args);
    }

    public int sendInsertRegistrationVerifications(Object... args) {
        return db.update("INSERT INTO registration_verifications(email,challenge_id,code_hash,expires_at,sent_at,window_started_at,send_count) VALUES (?,?,?,?,?,?,?)", args);
    }

    public int sendUpdateRegistrationVerifications(Object... args) {
        return db.update("UPDATE registration_verifications SET challenge_id=?,code_hash=?,attempts=0,expires_at=?,sent_at=?,window_started_at=?,send_count=?,verification_token_hash=NULL,verified_until=NULL,consumed_at=NULL WHERE email=?", args);
    }

    public List<Map<String, Object>> verifyFindRegistrationVerificationsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM registration_verifications WHERE challenge_id=? FOR UPDATE", args);
    }

    public int verifyUpdateRegistrationVerifications(Object... args) {
        return db.update("UPDATE registration_verifications SET attempts=attempts+1 WHERE challenge_id=?", args);
    }

    public int verifyUpdateRegistrationVerifications2(Object... args) {
        return db.update("UPDATE registration_verifications SET verification_token_hash=?,verified_until=? WHERE challenge_id=?", args);
    }

    public List<Map<String, Object>> consumeFindRegistrationVerificationsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM registration_verifications WHERE verification_token_hash=? FOR UPDATE", args);
    }

    public int consumeUpdateRegistrationVerifications(Object... args) {
        return db.update("UPDATE registration_verifications SET consumed_at=CURRENT_TIMESTAMP WHERE email=?", args);
    }
}
