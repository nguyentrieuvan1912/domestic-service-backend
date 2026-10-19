package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {
}
