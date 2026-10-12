package com.domesticservice.catalog.controller;

import com.domesticservice.catalog.application.CatalogQueryService;
import com.domesticservice.catalog.dto.CatalogResponse.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogQueryService queries;

    public CatalogController(CatalogQueryService queries) { this.queries = queries; }

    @GetMapping("/categories")
    public List<Category> categories() { return queries.categories(); }

    @GetMapping("/services")
    public Page services(@RequestParam(required = false) Long categoryId,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "12") int size) {
        return queries.services(categoryId, q, page, size);
    }

    @GetMapping("/services/{id}")
    public Detail detail(@PathVariable long id) { return queries.detail(id); }
}
