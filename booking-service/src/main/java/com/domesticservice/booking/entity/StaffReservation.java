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
 * Persistence model for staff_reservations; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_reservations")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "booking_id", nullable = true)
    private Long bookingId;

    @Column(name = "hold_token", nullable = false)
    private UUID holdToken;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Booking booking;

    public void setBooking(Booking booking) {
        this.booking = booking;
        this.bookingId = booking == null ? null : booking.getId();
    }
}
