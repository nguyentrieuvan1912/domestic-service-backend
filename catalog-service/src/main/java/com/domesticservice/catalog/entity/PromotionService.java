package com.domesticservice.catalog.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for promotion_services; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "promotion_services")
@DynamicInsert
@DynamicUpdate
@IdClass(PromotionServiceId.class)
@Getter
@Setter
@NoArgsConstructor
public class PromotionService {

    @Id
    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Id
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Service service;

    public void setPromotion(Promotion promotion) {
        this.promotion = promotion;
        this.promotionId = promotion == null ? null : promotion.getId();
    }

    public void setService(Service service) {
        this.service = service;
        this.serviceId = service == null ? null : service.getId();
    }
}
