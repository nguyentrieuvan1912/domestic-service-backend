package com.domesticservice.notification.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for notification_deliveries; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "notification_deliveries")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = true)
    private Long deviceId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "provider_reference", nullable = true, columnDefinition = "text")
    private String providerReference;

    @Column(name = "last_error", nullable = true, columnDefinition = "text")
    private String lastError;

    @Column(name = "next_retry_at", nullable = true)
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at", nullable = true)
    private Instant sentAt;

    @Column(name = "delivered_at", nullable = true)
    private Instant deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns({ @JoinColumn(name = "device_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false) })
    private NotificationDevice device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({ @JoinColumn(name = "notification_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false) })
    private Notification notification;

    public void setDevice(NotificationDevice device) {
        this.device = device;
        this.deviceId = device == null ? null : device.getId();
        this.userId = device == null ? null : device.getUserId();
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
        this.notificationId = notification == null ? null : notification.getId();
        this.userId = notification == null ? null : notification.getUserId();
    }
}
