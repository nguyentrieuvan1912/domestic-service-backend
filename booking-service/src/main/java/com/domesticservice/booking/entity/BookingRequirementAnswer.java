package com.domesticservice.booking.entity;

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
 * Persistence model for booking_requirement_answers; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "booking_requirement_answers")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BookingRequirementAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "label_snapshot", nullable = false, length = 160)
    private String labelSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_value", nullable = false, columnDefinition = "jsonb")
    private String answerValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Booking booking;

    public void setBooking(Booking booking) {
        this.booking = booking;
        this.bookingId = booking == null ? null : booking.getId();
    }
}
