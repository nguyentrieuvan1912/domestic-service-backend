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
 * Persistence model for knowledge_documents; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "knowledge_documents")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "source_uri", nullable = true, columnDefinition = "text")
    private String sourceUri;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<KnowledgeChunk> knowledgeChunksByDocument = new LinkedHashSet<>();
}
