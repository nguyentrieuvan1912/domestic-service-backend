package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.CustomerFavorite;
import com.domesticservice.identity.entity.CustomerFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerFavoriteRepository extends JpaRepository<CustomerFavorite, CustomerFavoriteId> {
}
