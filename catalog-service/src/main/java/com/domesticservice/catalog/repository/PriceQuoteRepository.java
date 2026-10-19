package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.entity.PriceQuote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, Long> {
}
