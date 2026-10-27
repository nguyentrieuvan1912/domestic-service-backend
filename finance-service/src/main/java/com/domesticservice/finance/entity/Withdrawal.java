package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for withdrawals; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "withdrawals")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_account_snapshot", nullable = false, columnDefinition = "jsonb")
    private String bankAccountSnapshot;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_by", nullable = true)
    private Long approvedBy;

    @Column(name = "approved_at", nullable = true)
    private Instant approvedAt;

    @Column(name = "paid_at", nullable = true)
    private Instant paidAt;

    @Column(name = "transfer_reference", nullable = true, length = 160)
    private String transferReference;

    @Column(name="cancelled_by") private Long cancelledBy;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancellation_reason",columnDefinition="text") private String cancellationReason;

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "request_key", nullable = true, length = 160)
    private String requestKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({ @JoinColumn(name = "bank_account_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "staff_id", referencedColumnName = "staff_id", insertable = false, updatable = false) })
    private StaffBankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({ @JoinColumn(name = "balance_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "staff_id", referencedColumnName = "staff_id", insertable = false, updatable = false) })
    private StaffBalance balance;

    public void setBankAccount(StaffBankAccount bankAccount) {
        this.bankAccount = bankAccount;
        this.bankAccountId = bankAccount == null ? null : bankAccount.getId();
        this.staffId = bankAccount == null ? null : bankAccount.getStaffId();
    }

    public void setBalance(StaffBalance balance) {
        this.balance = balance;
        this.balanceId = balance == null ? null : balance.getId();
        this.staffId = balance == null ? null : balance.getStaffId();
    }
}
