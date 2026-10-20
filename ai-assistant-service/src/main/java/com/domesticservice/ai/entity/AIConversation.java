package com.domesticservice.ai.entity;

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
 * Persistence model for ai_conversations; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "ai_conversations")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AIConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = true)
    private Instant endedAt;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<AIMessage> aIMessagesByConversation = new LinkedHashSet<>();

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<BookingDraft> bookingDraftsByConversation = new LinkedHashSet<>();
}
