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
 * Persistence model for add_ons; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "add_ons")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "extra_duration_minutes", nullable = false)
    private Integer extraDurationMinutes;

    @Column(name = "image_url", nullable = true, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Service service;

    public void setService(Service service) {
        this.service = service;
        this.serviceId = service == null ? null : service.getId();
    }
}
