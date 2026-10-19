package com.domesticservice.catalog.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for service_packages; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "service_packages")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "package_name", nullable = false, length = 160)
    private String packageName;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "base_price", nullable = false)
    private Long basePrice;

    @Column(name = "default_staff_count", nullable = false)
    private Integer defaultStaffCount;

    @Column(name = "max_area", nullable = true, precision = 10, scale = 2)
    private BigDecimal maxArea;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Service service;

    @OneToMany(mappedBy = "servicePackage", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<PriceQuote> priceQuotesByPackage = new LinkedHashSet<>();

    public void setService(Service service) {
        this.service = service;
        this.serviceId = service == null ? null : service.getId();
    }
}
