package com.domesticservice.ai.repository;

import com.domesticservice.ai.entity.BookingDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDraftRepository extends JpaRepository<BookingDraft, Long> {
}
