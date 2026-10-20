package com.domesticservice.identity.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="community_media") @Getter @Setter @NoArgsConstructor
public class CommunityMedia {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="post_id") private Long postId;
    @Column(name="comment_id") private Long commentId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="comment_id",insertable=false,updatable=false) private CommunityComment comment;
    @Column(name="public_id",nullable=false,length=200) private String publicId;
    @Column(name="resource_type",nullable=false,length=10) private String resourceType;
    @Column(nullable=false,length=12) private String status;
    @Column(name="secure_url",columnDefinition="text") private String secureUrl;
    private Long bytes;
    private Integer width;
    private Integer height;
    private Double duration;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",insertable=false,updatable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="post_id",insertable=false,updatable=false) private CommunityPost post;
}
