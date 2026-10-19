package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.AIConversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {
}
