package com.domesticservice.identity.repository;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import static com.domesticservice.identity.service.AccountService.error;
import org.springframework.http.HttpStatus;

@Repository
public class PartnerCaptchaRepository {
    private final JdbcTemplate db;
    public PartnerCaptchaRepository(JdbcTemplate db) { this.db = db; }

    @Transactional
    public void takeBudget(String operation, int limit) {
        var row = db.queryForMap("SELECT * FROM partner_request_budgets WHERE operation=? FOR UPDATE", operation);
        Instant now = Instant.now();
        boolean reset = db.queryForObject("SELECT window_started_at <= ? FROM partner_request_budgets WHERE operation=?",
                Boolean.class, Timestamp.from(now.minusSeconds(60)), operation);
        int count = reset ? 0 : ((Number) row.get("request_count")).intValue();
        if (count >= limit) throw error(HttpStatus.TOO_MANY_REQUESTS, "Có quá nhiều yêu cầu. Vui lòng chờ một phút rồi thử lại.");
        db.update("UPDATE partner_request_budgets SET request_count=?, window_started_at=CASE WHEN ? THEN ? ELSE window_started_at END WHERE operation=?",
                count + 1, reset, Timestamp.from(now), operation);
    }

    @Transactional
    public void create(String id, String hash, Instant expiry) {
        db.update("DELETE FROM partner_captchas WHERE expires_at <= ?", Timestamp.from(Instant.now()));
        db.update("INSERT INTO partner_captchas(id,answer_hash,expires_at) VALUES(?,?,?)", id, hash, Timestamp.from(expiry));
    }

    // Commit consumption even for an incorrect answer or a subsequent invalid profile.
    @Transactional
    public boolean consume(String id, String hash) {
        var rows = db.queryForList("SELECT * FROM partner_captchas WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        boolean fresh = Boolean.TRUE.equals(db.queryForObject(
                "SELECT NOT consumed AND expires_at > ? FROM partner_captchas WHERE id=?", Boolean.class,
                Timestamp.from(Instant.now()), id));
        db.update("UPDATE partner_captchas SET consumed=TRUE WHERE id=?", id);
        return fresh && java.security.MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                row.get("answer_hash").toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
