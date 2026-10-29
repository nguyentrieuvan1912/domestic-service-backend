package com.domesticservice.identity.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="community_comment_reports") @Getter @Setter @NoArgsConstructor
public class CommunityCommentReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="comment_id",nullable=false) private Long commentId;
    @Column(name="reporter_id",nullable=false) private Long reporterId;
    @Column(nullable=false,length=20) private String reason;
    @Column(nullable=false,columnDefinition="text") private String detail;
    @Column(nullable=false,length=12) private String status;
    @Column(length=24) private String resolution;
    @Column(name="moderation_note",columnDefinition="text") private String moderationNote;
    @Column(name="reviewed_by") private Long reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="comment_id",insertable=false,updatable=false) private CommunityComment comment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reporter_id",insertable=false,updatable=false) private User reporter;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by",insertable=false,updatable=false) private User reviewer;
}
