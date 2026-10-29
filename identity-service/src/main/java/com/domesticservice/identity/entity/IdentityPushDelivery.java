package com.domesticservice.identity.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="identity_push_deliveries") @Getter @Setter @NoArgsConstructor
public class IdentityPushDelivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="notification_id") private Long notificationId;
    @Column(name="device_id") private Long deviceId;
    @Column(name="status",length=12) private String status;
    @Column(name="attempts") private Integer attempts;
    @Column(name="ticket_id",columnDefinition="text") private String ticketId;
    @Column(name="error_code",length=80) private String errorCode;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="notification_id",insertable=false,updatable=false) private Notification notification;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="device_id",insertable=false,updatable=false) private IdentityPushDevice device;
}
