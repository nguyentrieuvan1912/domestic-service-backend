package com.domesticservice.catalog.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domesticservice.catalog.repository.PromotionQuerySqlRepository;

@Service
@Transactional(readOnly = true)
public class PromotionQueryService {

    private final PromotionQuerySqlRepository repository;

    public PromotionQueryService(PromotionQuerySqlRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> promotions() {
        Map<Long, List<Long>> scopes = new HashMap<>();
        for (var scope : repository.promotionsFindPromotionServices()) scopes.computeIfAbsent(((Number) scope.get("promotion_id")).longValue(), key -> new ArrayList<>()).add(((Number) scope.get("service_id")).longValue());
        return repository.promotionsFindPromotions(scopes);
    }
}
