package com.domesticservice.identity.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.identity.service.AdministrativeUnitsService;

@RestController
@RequestMapping("/api/v1/identity/administrative-units")
public class AdministrativeUnitsController {

    private final AdministrativeUnitsService service;

    public AdministrativeUnitsController(AdministrativeUnitsService service) {
        this.service = service;
    }

    @GetMapping("/hcm")
    public Map<String, Object> hcm() {
        return service.hcm();
    }
}
