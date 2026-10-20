package com.domesticservice.finance.entity;

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
 * Persistence model for staff_incomes; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_incomes")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "working_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal workingHours;

    @Column(name = "service_amount", nullable = false)
    private Long serviceAmount;

    @Column(name = "platform_fee", nullable = false)
    private Long platformFee;

    @Column(name = "staff_amount", nullable = false)
    private Long staffAmount;

    @Column(name = "tip_amount", nullable = false)
    private Long tipAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at", nullable = true)
    private Instant settledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({ @JoinColumn(name = "balance_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "staff_id", referencedColumnName = "staff_id", insertable = false, updatable = false) })
    private StaffBalance balance;

    public void setBalance(StaffBalance balance) {
        this.balance = balance;
        this.balanceId = balance == null ? null : balance.getId();
        this.staffId = balance == null ? null : balance.getStaffId();
    }
}
