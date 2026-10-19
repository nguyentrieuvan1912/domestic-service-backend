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
 * Persistence model for orders; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "orders")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "order_code", nullable = false, length = 60)
    private String orderCode;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    @Column(name = "add_on_amount", nullable = false)
    private Long addOnAmount;

    @Column(name = "extra_cost", nullable = false)
    private Long extraCost;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalized_at", nullable = true)
    private Instant finalizedAt;

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Invoice invoiceByOrder;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<OrderDetail> orderDetailsByOrder = new LinkedHashSet<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Payment> paymentsByOrder = new LinkedHashSet<>();
}
