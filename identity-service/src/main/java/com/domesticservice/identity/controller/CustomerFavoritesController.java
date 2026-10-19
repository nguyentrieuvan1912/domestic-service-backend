package com.domesticservice.identity.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.domesticservice.identity.service.CustomerFavoritesService;

@RestController
@RequestMapping("/api/v1/identity/customer/favorites")
public class CustomerFavoritesController {

    private final CustomerFavoritesService service;

    public CustomerFavoritesController(CustomerFavoritesService service) {
        this.service = service;
    }

    @GetMapping
    public List<Long> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.list(token);
    }

    @PutMapping("/{id}")
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.save(token, id, b);
    }
}
