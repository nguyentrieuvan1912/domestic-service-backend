package com.domesticservice.booking.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for staff_restrictions; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_restrictions")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "restriction_type", nullable = false, length = 30)
    private String restrictionType;

    @Column(name = "service_id", nullable = true)
    private Long serviceId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = true)
    private Instant endsAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_by", nullable = true)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;
}
