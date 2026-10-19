package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.StaffCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffCandidateRepository extends JpaRepository<StaffCandidate, Long> {
}
