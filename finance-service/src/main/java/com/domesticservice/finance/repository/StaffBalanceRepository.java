package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.StaffBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffBalanceRepository extends JpaRepository<StaffBalance, Long> {
}
