package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
