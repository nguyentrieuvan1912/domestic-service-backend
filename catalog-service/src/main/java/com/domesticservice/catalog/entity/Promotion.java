package com.domesticservice.catalog.entity;

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
 * Persistence model for promotions; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "promotions")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "promotion_code", nullable = false, length = 60)
    private String promotionCode;

    @Column(name = "promotion_name", nullable = false, length = 160)
    private String promotionName;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    @Column(name = "max_discount_amount", nullable = true)
    private Long maxDiscountAmount;

    @Column(name = "minimum_booking_amount", nullable = false)
    private Long minimumBookingAmount;

    @Column(name = "usage_limit", nullable = true)
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @OneToMany(mappedBy = "promotion", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<PromotionService> promotionServicesByPromotion = new LinkedHashSet<>();
}
