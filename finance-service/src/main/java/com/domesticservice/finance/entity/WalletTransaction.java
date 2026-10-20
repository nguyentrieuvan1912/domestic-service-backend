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
 * Persistence model for wallet_transactions; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "wallet_transactions")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "bucket", nullable = false, length = 15)
    private String bucket;

    @Column(name = "transaction_type", nullable = false, length = 40)
    private String transactionType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "reverses_transaction_id", nullable = true)
    private Long reversesTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_id", referencedColumnName = "id", insertable = false, updatable = false)
    private StaffBalance balance;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reverses_transaction_id", referencedColumnName = "id", insertable = false, updatable = false)
    private WalletTransaction reversedTransaction;

    @OneToMany(mappedBy = "reversedTransaction", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<WalletTransaction> walletTransactionsByReversedTransaction = new LinkedHashSet<>();

    public void setBalance(StaffBalance balance) {
        this.balance = balance;
        this.balanceId = balance == null ? null : balance.getId();
    }

    public void setReversedTransaction(WalletTransaction reversedTransaction) {
        this.reversedTransaction = reversedTransaction;
        this.reversesTransactionId = reversedTransaction == null ? null : reversedTransaction.getId();
    }
}
