package com.domesticservice.identity.service;

import com.domesticservice.identity.service.AccountService;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import com.domesticservice.identity.repository.CustomerFavoritesSqlRepository;

@Service()
public class CustomerFavoritesService {

    private final CustomerFavoritesSqlRepository repository;

    private final AccountService accounts;

    private final ServiceHttpClient catalog;

    public CustomerFavoritesService(CustomerFavoritesSqlRepository repository, AccountService accounts, @Value("${clients.catalog.base-url}") String url) {
        this.repository = repository;
        this.accounts = accounts;
        catalog = new ServiceHttpClient(url);
    }

    private long customer(String token) {
        var user = accounts.authenticate(token, false);
        if (!"CUSTOMER".equals(user.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ khách hàng sử dụng chức năng này.");
        return user.id();
    }

    public List<Long> list(String token) {
        return repository.listFindCustomerFavorites(Long.class, customer(token));
    }

    @Transactional
    public Map<String, Object> save(String token, long id, Map<String, Object> b) {
        long user = customer(token);
        if (!(b.get("favorite") instanceof Boolean selected))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lưu dịch vụ không hợp lệ.");
        if (selected) {
            catalog.get("/api/v1/catalog/services/" + id, token);
            repository.saveInsertCustomerFavorites(user, id);
        } else
            repository.saveDeleteCustomerFavorites(user, id);
        return Map.of("serviceId", id, "favorite", selected);
    }
}
