package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for registration_verifications; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "registration_verifications")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class RegistrationVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "challenge_id", nullable = false, length = 36)
    private String challengeId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "send_count", nullable = false)
    private Integer sendCount;

    @Column(name = "verification_token_hash", nullable = true, length = 64)
    private String verificationTokenHash;

    @Column(name = "verified_until", nullable = true)
    private Instant verifiedUntil;

    @Column(name = "consumed_at", nullable = true)
    private Instant consumedAt;
}
