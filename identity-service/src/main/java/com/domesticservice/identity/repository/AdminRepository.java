package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}
