package com.domesticservice.identity.entity;

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
 * Persistence model for users; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "users")
@DynamicInsert
@DynamicUpdate
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", nullable = true, length = 254)
    private String email;

    @Column(name = "avatar_url", nullable = true, columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at", nullable = true)
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "status_note", nullable = false, columnDefinition = "text")
    private String statusNote;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<AccountSession> accountSessionsByUser = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityComment> communityCommentsByUser = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityLike> communityLikesByUser = new LinkedHashSet<>();

    @OneToMany(mappedBy = "reviewer", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityPost> communityPostsByReviewer = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityPost> communityPostsByUser = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Notification> notificationsByUser = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<RefreshToken> refreshTokensByUser = new LinkedHashSet<>();
}
