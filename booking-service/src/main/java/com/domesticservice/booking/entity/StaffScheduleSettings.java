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
 * Persistence model for staff_schedule_settings; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staff_schedule_settings")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StaffScheduleSettings {

    @Id
    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "revision", nullable = false)
    private Long revision;

    @Column(name = "match_notifications", nullable = false)
    private Boolean matchNotifications;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
