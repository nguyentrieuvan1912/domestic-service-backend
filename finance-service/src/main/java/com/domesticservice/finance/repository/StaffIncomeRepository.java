package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.StaffIncome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffIncomeRepository extends JpaRepository<StaffIncome, Long> {
}
