package com.domesticservice.catalog.controller;

import com.domesticservice.catalog.service.PromotionQueryService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/promotions")
public class PromotionController {

    private final PromotionQueryService queries;

    public PromotionController(PromotionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return queries.promotions();
    }
}
