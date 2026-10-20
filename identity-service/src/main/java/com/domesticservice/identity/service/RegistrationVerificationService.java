package com.domesticservice.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.domesticservice.identity.service.AccountService.error;
import com.domesticservice.identity.repository.RegistrationVerificationSqlRepository;
import com.domesticservice.identity.dto.RegistrationResponse.*;

@Service
@Transactional
public class RegistrationVerificationService {

    private final RegistrationVerificationSqlRepository repository;

    private final RegistrationMailer mailer;

    private final SecureRandom random = new SecureRandom();

    private final BCryptPasswordEncoder codes = new BCryptPasswordEncoder(12);

    public RegistrationVerificationService(RegistrationVerificationSqlRepository repository, RegistrationMailer mailer) {
        this.repository = repository;
        this.mailer = mailer;
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Challenge send(String address) {
        String email = address.strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[a-z0-9][a-z0-9.+_-]*@gmail\\.com"))
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ Gmail hợp lệ (@gmail.com).");
        if (Boolean.TRUE.equals(repository.sendExistsUsers(Boolean.class, email)))
            throw error(HttpStatus.CONFLICT, "Gmail này đã được đăng ký. Vui lòng đăng nhập.");
        var rows = repository.sendFindRegistrationVerificationsForUpdate(email);
        Instant now = Instant.now(), window = now;
        int count = 1;
        if (!rows.isEmpty()) {
            var row = rows.getFirst();
            if (instant(row, "sent_at").plusSeconds(60).isAfter(now))
                throw error(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây trước khi gửi lại mã.");
            window = instant(row, "window_started_at");
            if (window.plusSeconds(3600).isAfter(now)) {
                count = ((Number) row.get("send_count")).intValue() + 1;
                if (count > 5)
                    throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn gửi mã. Vui lòng thử lại sau một giờ.");
            } else
                window = now;
        }
        String id = UUID.randomUUID().toString(), code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        Instant expiry = now.plusSeconds(600);
        if (rows.isEmpty()) {
            try {
                repository.sendInsertRegistrationVerifications(email, id, codes.encode(code), Timestamp.from(expiry), Timestamp.from(now), Timestamp.from(window), count);
            } catch (DataIntegrityViolationException ex) {
                throw error(HttpStatus.CONFLICT, "Yêu cầu gửi mã đang được xử lý. Vui lòng thử lại sau.");
            }
        } else
            repository.sendUpdateRegistrationVerifications(id, codes.encode(code), Timestamp.from(expiry), Timestamp.from(now), Timestamp.from(window), count, email);
        // A failed delivery rolls back the challenge; never return success for an unsent email.
        mailer.send(email, code);
        return new Challenge(id, email, expiry, now.plusSeconds(60));
    }

    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof java.time.OffsetDateTime time ? time.toInstant() : ((Timestamp) value).toInstant();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Verified verify(String id, String code) {
        var rows = repository.verifyFindRegistrationVerificationsForUpdate(id);
        if (rows.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Mã xác thực không hợp lệ hoặc đã được gửi lại.");
        var row = rows.getFirst();
        Instant now = Instant.now();
        if (row.get("consumed_at") != null || row.get("verification_token_hash") != null)
            throw error(HttpStatus.CONFLICT, "Yêu cầu này đã được xác thực. Vui lòng tiếp tục hoặc gửi lại mã.");
        if (!instant(row, "expires_at").isAfter(now))
            throw error(HttpStatus.BAD_REQUEST, "Mã đã hết hạn. Vui lòng gửi lại mã.");
        if (((Number) row.get("attempts")).intValue() >= 5)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai mã 5 lần. Vui lòng gửi lại mã.");
        if (!codes.matches(code, (String) row.get("code_hash"))) {
            repository.verifyUpdateRegistrationVerifications(id);
            throw error(HttpStatus.BAD_REQUEST, "Mã xác thực không đúng.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant until = now.plusSeconds(1200);
        repository.verifyUpdateRegistrationVerifications2(digest(token), Timestamp.from(until), id);
        return new Verified(token, (String) row.get("email"), until);
    }

    // Called inside the account transaction: lock and consume only together with user creation.
    public String consume(String token) {
        var rows = repository.consumeFindRegistrationVerificationsForUpdate(digest(token));
        if (rows.isEmpty() || rows.getFirst().get("consumed_at") != null || !instant(rows.getFirst(), "verified_until").isAfter(Instant.now()))
            throw error(HttpStatus.FORBIDDEN, "Vui lòng xác thực Gmail trước khi tạo tài khoản.");
        String email = (String) rows.getFirst().get("email");
        repository.consumeUpdateRegistrationVerifications(email);
        return email;
    }
}
