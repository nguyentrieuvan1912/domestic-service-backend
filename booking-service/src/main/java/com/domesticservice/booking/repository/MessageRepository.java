package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
