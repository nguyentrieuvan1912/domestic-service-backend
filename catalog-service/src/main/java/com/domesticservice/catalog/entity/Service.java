package com.domesticservice.catalog.entity;

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
 * Persistence model for services; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "services")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "service_code", nullable = false, length = 60)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 160)
    private String serviceName;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "short_description", nullable = true, columnDefinition = "text")
    private String shortDescription;

    @Column(name = "service_type", nullable = false, length = 60)
    private String serviceType;

    @Column(name = "price_unit", nullable = false, length = 30)
    private String priceUnit;

    @Column(name = "base_price", nullable = false)
    private Long basePrice;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private Integer estimatedDurationMinutes;

    @Column(name = "requires_qualification", nullable = false)
    private Boolean requiresQualification;

    @Column(name = "image_url", nullable = true, columnDefinition = "text")
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights", nullable = false, columnDefinition = "jsonb")
    private String highlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow", nullable = false, columnDefinition = "jsonb")
    private String workflow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", nullable = false, columnDefinition = "jsonb")
    private String benefits;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ServiceCategory category;

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<AddOn> addOnsByService = new LinkedHashSet<>();

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<PriceQuote> priceQuotesByService = new LinkedHashSet<>();

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<PromotionService> promotionServicesByService = new LinkedHashSet<>();

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<ServicePackage> servicePackagesByService = new LinkedHashSet<>();

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<ServiceRequirement> serviceRequirementsByService = new LinkedHashSet<>();

    public void setCategory(ServiceCategory category) {
        this.category = category;
        this.categoryId = category == null ? null : category.getId();
    }
}
