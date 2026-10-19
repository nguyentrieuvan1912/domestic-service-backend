package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.BookingIncident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingIncidentRepository extends JpaRepository<BookingIncident, Long> {
}
