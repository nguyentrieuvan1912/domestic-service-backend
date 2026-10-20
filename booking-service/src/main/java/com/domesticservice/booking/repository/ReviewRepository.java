package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
