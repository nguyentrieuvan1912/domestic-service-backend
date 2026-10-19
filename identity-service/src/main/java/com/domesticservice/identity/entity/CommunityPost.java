package com.domesticservice.identity.entity;

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
 * Persistence model for community_posts; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "community_posts")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_urls", nullable = false, columnDefinition = "jsonb")
    private String mediaUrls;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "moderation_note", nullable = true, columnDefinition = "text")
    private String moderationNote;

    @Column(name = "reviewed_by", nullable = true)
    private Long reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityComment> communityCommentsByPost = new LinkedHashSet<>();

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CommunityLike> communityLikesByPost = new LinkedHashSet<>();

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
        this.reviewedBy = reviewer == null ? null : reviewer.getId();
    }

    public void setUser(User user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }
}
