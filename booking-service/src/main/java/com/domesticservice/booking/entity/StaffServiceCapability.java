package com.domesticservice.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for staff_service_capabilities; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_service_capabilities")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffServiceCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "skill_level", nullable = false, length = 30)
    private String skillLevel;

    @Column(name = "experience_years", nullable = false, precision = 4, scale = 1)
    private BigDecimal experienceYears;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "approved_at", nullable = true)
    private Instant approvedAt;

    @Column(name = "approved_by", nullable = true)
    private Long approvedBy;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    @Column(name = "verified_by", nullable = true)
    private Long verifiedBy;

    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    @Column(name = "valid_from", nullable = true)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = true)
    private Instant validUntil;

    @Column(name = "staff_may_view_note", nullable = false)
    private Boolean staffMayViewNote;
}
