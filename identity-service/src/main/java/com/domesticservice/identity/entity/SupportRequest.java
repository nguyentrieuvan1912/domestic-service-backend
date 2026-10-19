package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for support_requests; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "support_requests")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SupportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sender_name", nullable = false, length = 160)
    private String senderName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "admin_note", nullable = false, columnDefinition = "text")
    private String adminNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
