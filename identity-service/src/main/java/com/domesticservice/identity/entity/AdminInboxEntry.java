package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="admin_inbox_entries") @Getter @Setter @NoArgsConstructor
public class AdminInboxEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",insertable=false,updatable=false) private User user;
    @Column(nullable=false,length=30) private String source;
    @Column(name="source_key",nullable=false,length=160) private String sourceKey;
    @Column(name="event_key",nullable=false,length=200) private String eventKey;
    @Column(nullable=false,length=100) private String state;
    @Column(nullable=false,length=40) private String section;
    @Column(nullable=false,length=255) private String target;
    @Column(nullable=false,length=255) private String title;
    @Column(nullable=false,columnDefinition="text") private String summary;
    @Column(nullable=false) private boolean active;
    @Column(name="read_at") private Instant readAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
