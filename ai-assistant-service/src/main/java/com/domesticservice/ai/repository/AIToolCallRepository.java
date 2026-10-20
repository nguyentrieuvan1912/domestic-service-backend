package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.AIToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIToolCallRepository extends JpaRepository<AIToolCall, Long> {
}
