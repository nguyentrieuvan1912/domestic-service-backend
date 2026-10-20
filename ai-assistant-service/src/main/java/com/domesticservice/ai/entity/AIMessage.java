package com.domesticservice.ai.entity;

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
 * Persistence model for ai_messages; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "ai_messages")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AIMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions", nullable = false, columnDefinition = "jsonb")
    private String suggestions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "client_key", nullable = true, length = 160)
    private String clientKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", referencedColumnName = "id", insertable = false, updatable = false)
    private AIConversation conversation;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<AIToolCall> aIToolCallsByMessage = new LinkedHashSet<>();

    public void setConversation(AIConversation conversation) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
    }
}
