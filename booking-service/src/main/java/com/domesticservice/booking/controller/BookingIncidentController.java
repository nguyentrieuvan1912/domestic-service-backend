package com.domesticservice.booking.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.booking.service.BookingIncidentService;

@RestController
@RequestMapping("/api/v1/booking/admin/incidents")
public class BookingIncidentController {

    private final BookingIncidentService service;

    public BookingIncidentController(BookingIncidentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.list(token);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.save(token, id, b);
    }
}
