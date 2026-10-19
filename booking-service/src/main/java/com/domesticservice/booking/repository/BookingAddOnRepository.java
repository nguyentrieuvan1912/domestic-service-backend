package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.BookingAddOn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingAddOnRepository extends JpaRepository<BookingAddOn, Long> {
}
