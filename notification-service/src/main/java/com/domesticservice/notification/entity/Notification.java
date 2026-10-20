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
 * Persistence model for notifications; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "notifications")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_role", nullable = true, length = 20)
    private String targetRole;

    @Column(name = "event_id", nullable = true)
    private Long eventId;

    @Column(name = "notification_type", nullable = false, length = 80)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "reference_type", nullable = true, length = 80)
    private String referenceType;

    @Column(name = "reference_id", nullable = true)
    private Long referenceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "event_id", referencedColumnName = "id", insertable = false, updatable = false)
    private NotificationEvent event;

    @OneToMany(mappedBy = "notification", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<NotificationDelivery> notificationDeliverysByNotification = new LinkedHashSet<>();

    public void setEvent(NotificationEvent event) {
        this.event = event;
        this.eventId = event == null ? null : event.getId();
    }
}
