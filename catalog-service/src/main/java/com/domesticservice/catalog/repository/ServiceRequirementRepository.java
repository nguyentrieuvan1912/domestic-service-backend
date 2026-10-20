package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.ServiceRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequirementRepository extends JpaRepository<ServiceRequirement, Long> {
}
