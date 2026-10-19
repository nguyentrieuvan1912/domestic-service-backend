package com.domesticservice.identity.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.domesticservice.identity.repository.AdministrativeUnitsSqlRepository;

@Service()
public class AdministrativeUnitsService {

    private final AdministrativeUnitsSqlRepository repository;

    public AdministrativeUnitsService(AdministrativeUnitsSqlRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> hcm() {
        var items = repository.hcmFindAdministrativeUnits();
        return Map.of("province", "TP. Hồ Chí Minh", "effectiveFrom", "2025-07-01", "resolution", "1685/NQ-UBTVQH15", "items", items, "total", items.size());
    }
}
