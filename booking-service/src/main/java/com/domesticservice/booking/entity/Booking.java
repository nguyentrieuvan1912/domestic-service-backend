package com.domesticservice.booking.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for bookings; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "bookings")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 60)
    private String bookingCode;

    @Column(name = "booking_mode", nullable = false, length = 10)
    private String bookingMode;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "package_id", nullable = true)
    private Long packageId;

    @Column(name = "address_id", nullable = false)
    private Long addressId;

    @Column(name = "quote_id", nullable = true)
    private Long quoteId;

    @Column(name = "quote_version", nullable = true)
    private Long quoteVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_snapshot", nullable = false, columnDefinition = "jsonb")
    private String serviceSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_snapshot", nullable = false, columnDefinition = "jsonb")
    private String addressSnapshot;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "required_staff_count", nullable = false)
    private Integer requiredStaffCount;

    @Column(name = "base_amount", nullable = false)
    private Long baseAmount;

    @Column(name = "add_on_amount", nullable = false)
    private Long addOnAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "promotion_id", nullable = true)
    private Long promotionId;

    @Column(name = "promotion_code", nullable = true, length = 60)
    private String promotionCode;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus;

    @Column(name = "notes", nullable = true, columnDefinition = "text")
    private String notes;

    @Column(name = "cancel_reason", nullable = true, columnDefinition = "text")
    private String cancelReason;

    @Column(name = "cancelled_by", nullable = true)
    private Long cancelledBy;

    @Column(name = "cancelled_by_type", nullable = true, length = 20)
    private String cancelledByType;

    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;

    @Column(name = "confirmed_at", nullable = true)
    private Instant confirmedAt;

    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "request_key", nullable = true, length = 160)
    private String requestKey;

    @Column(name = "request_hash", nullable = true, length = 128)
    private String requestHash;

    @Column(name = "pricing_confirmed", nullable = false)
    private Boolean pricingConfirmed;

    @Column(name = "staff_response_deadline", nullable = true)
    private Instant staffResponseDeadline;

    @Column(name = "staff_wait_state", nullable = true, length = 10)
    private String staffWaitState;

    @Column(name = "staff_wait_round", nullable = false)
    private Integer staffWaitRound;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingAddOn> bookingAddOnsByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingAssignment> bookingAssignmentsByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingIncident> bookingIncidentsByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingRequirementAnswer> bookingRequirementAnswersByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingStatusHistory> bookingStatusHistorysByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Conversation> conversationsByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Review> reviewsByBooking = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<StaffReservation> staffReservationsByBooking = new LinkedHashSet<>();
}
