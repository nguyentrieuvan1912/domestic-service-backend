package com.domesticservice.booking.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.booking.service.PublicStaffStatisticsService;

@RestController
@RequestMapping("/api/v1/booking/public")
public class PublicStaffStatisticsController {

    private final PublicStaffStatisticsService service;

    public PublicStaffStatisticsController(PublicStaffStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/staff-stats")
    public List<Map<String, Object>> stats() {
        return service.stats();
    }

    @GetMapping("/staff/{id}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable long id) {
        return service.reviews(id);
    }
}
