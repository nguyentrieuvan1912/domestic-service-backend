package com.domesticservice.notification.entity;

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
 * Persistence model for notification_events; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "notification_events")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "last_error", nullable = true, columnDefinition = "text")
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = true)
    private Instant processedAt;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Notification> notificationsByEvent = new LinkedHashSet<>();
}
