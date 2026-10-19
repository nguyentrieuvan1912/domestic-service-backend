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
 * Persistence model for staff_balances; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_balances")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "available_balance", nullable = false)
    private Long availableBalance;

    @Column(name = "pending_balance", nullable = false)
    private Long pendingBalance;

    @Column(name = "reserved_balance", nullable = false)
    private Long reservedBalance;

    @Column(name = "minimum_balance", nullable = false)
    private Long minimumBalance;

    @Column(name = "total_earned", nullable = false)
    private Long totalEarned;

    @Column(name = "withdrawn_amount", nullable = false)
    private Long withdrawnAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "balance", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<StaffIncome> staffIncomesByBalance = new LinkedHashSet<>();

    @OneToMany(mappedBy = "balance", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<WalletTransaction> walletTransactionsByBalance = new LinkedHashSet<>();

    @OneToMany(mappedBy = "balance", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Withdrawal> withdrawalsByBalance = new LinkedHashSet<>();
}
