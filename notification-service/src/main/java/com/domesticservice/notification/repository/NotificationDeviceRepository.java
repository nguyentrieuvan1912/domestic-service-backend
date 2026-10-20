package com.domesticservice.notification.repository;

import com.domesticservice.notification.entity.NotificationDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, Long> {
}
