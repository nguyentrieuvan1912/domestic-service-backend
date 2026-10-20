package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
