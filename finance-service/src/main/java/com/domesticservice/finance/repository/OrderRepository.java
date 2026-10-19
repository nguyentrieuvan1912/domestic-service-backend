package com.domesticservice.finance.repository;

import com.domesticservice.finance.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
