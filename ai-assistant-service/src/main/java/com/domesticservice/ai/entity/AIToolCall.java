package com.domesticservice.ai.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for ai_tool_calls; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "ai_tool_calls")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AIToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "draft_id", nullable = true)
    private Long draftId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments", nullable = false, columnDefinition = "jsonb")
    private String arguments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = true, columnDefinition = "jsonb")
    private String result;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "draft_id", referencedColumnName = "id", insertable = false, updatable = false)
    private BookingDraft draft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", referencedColumnName = "id", insertable = false, updatable = false)
    private AIMessage message;

    public void setDraft(BookingDraft draft) {
        this.draft = draft;
        this.draftId = draft == null ? null : draft.getId();
    }

    public void setMessage(AIMessage message) {
        this.message = message;
        this.messageId = message == null ? null : message.getId();
    }
}
