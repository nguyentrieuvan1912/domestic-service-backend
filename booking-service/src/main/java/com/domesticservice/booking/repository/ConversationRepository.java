package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
