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
 * Persistence model for booking_status_histories; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "booking_status_histories")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "old_status", nullable = true, length = 30)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private String newStatus;

    @Column(name = "changed_by", nullable = true)
    private Long changedBy;

    @Column(name = "changed_by_type", nullable = false, length = 20)
    private String changedByType;

    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Booking booking;

    public void setBooking(Booking booking) {
        this.booking = booking;
        this.bookingId = booking == null ? null : booking.getId();
    }
}
