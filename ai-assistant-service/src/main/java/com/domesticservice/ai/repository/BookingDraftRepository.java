package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.BookingDraft;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDraftRepository extends JpaRepository<BookingDraft, Long> {
    Optional<BookingDraft> findFirstByConversationIdAndCustomerIdAndStatusAndExpiresAtAfterOrderByIdDesc(
        long conversationId, long customerId, String status, Instant now);
}
