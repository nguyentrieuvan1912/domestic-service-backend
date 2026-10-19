package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.StaffBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffBankAccountRepository extends JpaRepository<StaffBankAccount, Long> {
}
