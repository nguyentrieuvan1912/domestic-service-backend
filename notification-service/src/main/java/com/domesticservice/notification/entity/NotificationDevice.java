package com.domesticservice.notification.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for notification_devices; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "notification_devices")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class NotificationDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_key", nullable = false, length = 200)
    private String deviceKey;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "push_token", nullable = false, columnDefinition = "text")
    private String pushToken;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at", nullable = true)
    private Instant lastSeenAt;

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<NotificationDelivery> notificationDeliverysByDevice = new LinkedHashSet<>();
}
