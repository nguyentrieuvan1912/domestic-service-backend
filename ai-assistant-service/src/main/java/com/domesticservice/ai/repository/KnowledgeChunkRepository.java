package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
}
