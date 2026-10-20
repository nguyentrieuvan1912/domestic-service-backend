package com.domesticservice.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
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
 * Persistence model for booking_assignments; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "booking_assignments")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BookingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "matching_score", nullable = true, precision = 8, scale = 4)
    private BigDecimal matchingScore;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "accepted_at", nullable = true)
    private Instant acceptedAt;

    @Column(name = "rejected_at", nullable = true)
    private Instant rejectedAt;

    @Column(name = "cancellation_reason", nullable = true, columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "execution_status", nullable = false, length = 20)
    private String executionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", nullable = false, columnDefinition = "jsonb")
    private String checklist;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staff_snapshot", nullable = false, columnDefinition = "jsonb")
    private String staffSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Booking booking;

    public void setBooking(Booking booking) {
        this.booking = booking;
        this.bookingId = booking == null ? null : booking.getId();
    }
}
