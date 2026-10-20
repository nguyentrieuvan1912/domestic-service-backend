package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, Long> {
}
