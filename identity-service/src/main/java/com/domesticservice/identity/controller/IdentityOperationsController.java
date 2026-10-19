package com.domesticservice.identity.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.identity.service.IdentityOperationsService;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityOperationsController {

    private final IdentityOperationsService service;

    public IdentityOperationsController(IdentityOperationsService service) {
        this.service = service;
    }

    @PostMapping("/internal/events")
    public Map<String, Object> event(@RequestHeader(value = "X-Service-Key", required = false) String key, @RequestBody Map<String, Object> b) {
        return service.event(key, b);
    }

    @GetMapping("/admin/support-requests")
    public List<Map<String, Object>> support(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.support(token);
    }

    @PatchMapping("/admin/support-requests/{id}")
    public Map<String, Object> support(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.support(token, id, b);
    }
}
