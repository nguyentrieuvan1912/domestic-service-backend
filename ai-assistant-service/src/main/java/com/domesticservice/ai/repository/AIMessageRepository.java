package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIMessageRepository extends JpaRepository<AIMessage, Long> {
}
