package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for payments; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "payments")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "order_id", nullable = true)
    private Long orderId;

    @Column(name = "payment_code", nullable = false, length = 60)
    private String paymentCode;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "provider", nullable = true, length = 60)
    private String provider;

    @Column(name = "provider_reference", nullable = true, length = 160)
    private String providerReference;

    @Column(name = "transaction_code", nullable = true, length = 160)
    private String transactionCode;

    @Column(name = "qr_content", nullable = true, columnDefinition = "text")
    private String qrContent;

    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "paid_at", nullable = true)
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "admin_note", nullable = false, columnDefinition = "text")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Order order;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<PaymentWebhookEvent> paymentWebhookEventsByPayment = new LinkedHashSet<>();

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Refund> refundsByPayment = new LinkedHashSet<>();

    public void setOrder(Order order) {
        this.order = order;
        this.orderId = order == null ? null : order.getId();
    }
}
