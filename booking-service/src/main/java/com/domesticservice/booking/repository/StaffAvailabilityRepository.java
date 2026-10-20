package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.StaffAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAvailabilityRepository extends JpaRepository<StaffAvailability, Long> {
}
