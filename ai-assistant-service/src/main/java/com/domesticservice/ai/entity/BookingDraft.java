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
 * Persistence model for booking_drafts; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "booking_drafts")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BookingDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_data", nullable = false, columnDefinition = "jsonb")
    private String draftData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_slots", nullable = false, columnDefinition = "jsonb")
    private String missingSlots;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_snapshot", nullable = true, columnDefinition = "jsonb")
    private String previewSnapshot;

    @Column(name = "quote_id", nullable = true)
    private Long quoteId;

    @Column(name = "booking_id", nullable = true)
    private Long bookingId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at", nullable = true)
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", referencedColumnName = "id", insertable = false, updatable = false)
    private AIConversation conversation;

    @OneToMany(mappedBy = "draft", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<AIToolCall> aIToolCallsByDraft = new LinkedHashSet<>();

    public void setConversation(AIConversation conversation) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
    }
}
