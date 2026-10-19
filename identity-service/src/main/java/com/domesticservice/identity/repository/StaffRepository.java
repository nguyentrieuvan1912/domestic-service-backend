package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {
}
