package com.domesticservice.finance.controller;

import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.finance.service.FinanceStatisticsService;

@RestController
@RequestMapping("/api/v1/finance/admin")
public class FinanceStatisticsController {

    private final FinanceStatisticsService service;

    public FinanceStatisticsController(FinanceStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String token, @RequestParam(defaultValue = "2026") int year) {
        return service.stats(token, year);
    }
}
