package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for refunds; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "refunds")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "transaction_code", nullable = true, length = 160)
    private String transactionCode;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_by", nullable = true)
    private Long approvedBy;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "admin_note", nullable = true, columnDefinition = "text")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Payment payment;

    public void setPayment(Payment payment) {
        this.payment = payment;
        this.paymentId = payment == null ? null : payment.getId();
    }
}
