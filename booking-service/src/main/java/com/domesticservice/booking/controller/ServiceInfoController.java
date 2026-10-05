package com.domesticservice.booking.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {
    @GetMapping("/api/v1/booking/system/info")
    public Map<String, String> info() {
        return Map.of("service", "booking-service", "stage", "scaffold");
    }
}
