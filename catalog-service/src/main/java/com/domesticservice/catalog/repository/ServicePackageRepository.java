package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {
}
