package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.BookingDraft;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookingDraftRepository extends JpaRepository<BookingDraft, Long> {
    Optional<BookingDraft> findByConversationIdAndCustomerIdAndStatusAndExpiresAtAfter(
        long conversationId, long customerId, String status, Instant now);

    @Modifying
    @Query("""
        update BookingDraft d set d.status = 'EXPIRED', d.version = d.version + 1
        where d.conversationId = :conversation and d.status = 'COLLECTING' and d.expiresAt <= :now
        """)
    void expireDraft(long conversation, Instant now);
}
