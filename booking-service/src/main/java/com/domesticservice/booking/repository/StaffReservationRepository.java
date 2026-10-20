package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.StaffReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffReservationRepository extends JpaRepository<StaffReservation, Long> {
}
