package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.FinanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceSettingsRepository extends JpaRepository<FinanceSettings, String> {
}
