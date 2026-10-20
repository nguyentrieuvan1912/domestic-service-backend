package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {
}
