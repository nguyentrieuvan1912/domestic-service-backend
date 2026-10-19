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
 * Persistence model for service_requirements; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "service_requirements")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false, columnDefinition = "jsonb")
    private String options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", nullable = false, columnDefinition = "jsonb")
    private String validationRules;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Service service;

    public void setService(Service service) {
        this.service = service;
        this.serviceId = service == null ? null : service.getId();
    }
}
