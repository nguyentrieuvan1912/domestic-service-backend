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
 * Persistence model for invoices; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "invoices")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "invoice_number", nullable = false, length = 60)
    private String invoiceNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "customer_snapshot", nullable = false, columnDefinition = "jsonb")
    private String customerSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_snapshot", nullable = false, columnDefinition = "jsonb")
    private String serviceSnapshot;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "vat_amount", nullable = false)
    private Long vatAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Order order;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<InvoiceItem> invoiceItemsByInvoice = new LinkedHashSet<>();

    public void setOrder(Order order) {
        this.order = order;
        this.orderId = order == null ? null : order.getId();
    }
}
