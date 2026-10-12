package com.domesticservice.catalog.application;

import com.domesticservice.catalog.dto.CatalogResponse.*;
import com.domesticservice.catalog.infrastructure.repository.CatalogReadRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {
    private final CatalogReadRepository repository;

    public CatalogQueryService(CatalogReadRepository repository) { this.repository = repository; }

    public List<Category> categories() { return repository.categories(); }

    public Page services(Long categoryId, String q, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || (categoryId != null && categoryId <= 0)
                || q.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page: 0..100000, size: 1..100, categoryId: positive, q: maximum 100 characters");
        }
        return repository.services(categoryId, q.strip(), page, size);
    }

    public Detail detail(long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service ID must be positive");
        return repository.detail(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found or inactive"));
    }
}
