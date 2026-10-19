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
 * Persistence model for knowledge_chunks; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "knowledge_chunks")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "search_vector", nullable = true, columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding", nullable = true, columnDefinition = "jsonb")
    private String embedding;

    @Column(name = "embedding_model", nullable = true, length = 100)
    private String embeddingModel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", referencedColumnName = "id", insertable = false, updatable = false)
    private KnowledgeDocument document;

    public void setDocument(KnowledgeDocument document) {
        this.document = document;
        this.documentId = document == null ? null : document.getId();
    }
}
