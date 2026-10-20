package com.domesticservice.booking.controller;

import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.booking.service.BookingStatisticsService;

@RestController
@RequestMapping("/api/v1/booking/admin")
public class BookingStatisticsController {

    private final BookingStatisticsService service;

    public BookingStatisticsController(BookingStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String token, @RequestParam(defaultValue = "2026") int year, @RequestParam(defaultValue = "year") String period) {
        return service.stats(token, year, period);
    }
}
