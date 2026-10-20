package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
}
