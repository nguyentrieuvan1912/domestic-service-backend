package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.BookingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingAssignmentRepository extends JpaRepository<BookingAssignment, Long> {
}
