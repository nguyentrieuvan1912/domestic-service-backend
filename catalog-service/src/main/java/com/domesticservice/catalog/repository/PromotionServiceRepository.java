package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.PromotionService;
import com.domesticservice.catalog.entity.PromotionServiceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionServiceRepository extends JpaRepository<PromotionService, PromotionServiceId> {
}
