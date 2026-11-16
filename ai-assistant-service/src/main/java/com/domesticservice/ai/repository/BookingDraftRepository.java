package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.BookingDraft;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDraftRepository extends JpaRepository<BookingDraft, Long> {
    List<BookingDraft> findByConversationIdAndCustomerIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
        long conversationId, long customerId, String status, Instant now);
}
