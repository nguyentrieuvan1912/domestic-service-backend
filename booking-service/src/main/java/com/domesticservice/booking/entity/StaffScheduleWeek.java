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
 * Persistence model for staff_schedule_weeks; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_schedule_weeks")
@DynamicInsert
@DynamicUpdate
@IdClass(StaffScheduleWeekId.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffScheduleWeek {

    @Id
    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
}
