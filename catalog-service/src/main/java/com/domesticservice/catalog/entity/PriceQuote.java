package com.domesticservice.catalog.entity;

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
 * Persistence model for price_quotes; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "price_quotes")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PriceQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "package_id", nullable = true)
    private Long packageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "base_amount", nullable = false)
    private Long baseAmount;

    @Column(name = "add_on_amount", nullable = false)
    private Long addOnAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "quote_version", nullable = false)
    private Long quoteVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "request_key", nullable = true, length = 160)
    private String requestKey;

    @Column(name = "request_hash", nullable = true, length = 128)
    private String requestHash;

    @Column(name = "consumed_booking_id", nullable = true)
    private Long consumedBookingId;

    @Column(name = "released_at", nullable = true)
    private Instant releasedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "package_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ServicePackage servicePackage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Service service;

    public void setServicePackage(ServicePackage servicePackage) {
        this.servicePackage = servicePackage;
        this.packageId = servicePackage == null ? null : servicePackage.getId();
    }

    public void setService(Service service) {
        this.service = service;
        this.serviceId = service == null ? null : service.getId();
    }
}
