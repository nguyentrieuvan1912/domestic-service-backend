package com.domesticservice.identity.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="identity_push_devices") @Getter @Setter @NoArgsConstructor
public class IdentityPushDevice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id") private Long userId;
    @Column(name="token",columnDefinition="text") private String token;
    @Column(name="session_hash",length=64) private String sessionHash;
    @Column(name="platform",length=10) private String platform;
    @Column(name="enabled") private Boolean enabled;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",insertable=false,updatable=false) private User user;
}
