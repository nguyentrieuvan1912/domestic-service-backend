package com.domesticservice.identity.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {
    @GetMapping("/api/v1/identity/system/info")
    public Map<String, String> info() {
        return Map.of("service", "identity-service", "stage", "scaffold");
    }
}
