package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.RegistrationVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationVerificationRepository extends JpaRepository<RegistrationVerification, Long> {
}
