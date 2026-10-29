package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for staff_bank_accounts; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_bank_accounts")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "bank_code", nullable = false, length = 30)
    private String bankCode;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 160)
    private String accountHolder;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_note", columnDefinition = "text")
    private String verificationNote;

    @OneToMany(mappedBy = "bankAccount", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Withdrawal> withdrawalsByBankAccount = new LinkedHashSet<>();
}
