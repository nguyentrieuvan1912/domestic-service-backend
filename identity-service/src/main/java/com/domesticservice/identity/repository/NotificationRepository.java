package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
