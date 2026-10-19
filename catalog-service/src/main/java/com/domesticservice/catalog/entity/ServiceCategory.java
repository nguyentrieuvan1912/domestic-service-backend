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
 * Persistence model for service_categories; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "service_categories")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "category_code", nullable = false, length = 60)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 160)
    private String categoryName;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "icon_url", nullable = true, columnDefinition = "text")
    private String iconUrl;

    @Column(name = "category_group", nullable = true, length = 30)
    private String categoryGroup;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Service> servicesByCategory = new LinkedHashSet<>();
}
