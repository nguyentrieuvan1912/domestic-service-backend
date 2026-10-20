package com.domesticservice.booking.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for messages; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "messages")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = true)
    private Long senderId;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "media_url", nullable = true, columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    @Column(name = "client_key", nullable = true, length = 160)
    private String clientKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Conversation conversation;

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
    }
}
