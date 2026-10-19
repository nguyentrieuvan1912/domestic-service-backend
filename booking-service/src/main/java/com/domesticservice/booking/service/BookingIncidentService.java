package com.domesticservice.booking.service;

import com.domesticservice.booking.service.BookingOperationsService;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import com.domesticservice.booking.repository.BookingIncidentSqlRepository;

@Service()
public class BookingIncidentService {

    private final BookingOperationsService ops;

    private final BookingIncidentSqlRepository repository;

    public BookingIncidentService(BookingOperationsService ops, BookingIncidentSqlRepository repository) {
        this.ops = ops;
        this.repository = repository;
    }

    public List<Map<String, Object>> list(String token) {
        ops.actor(token, "ADMIN");
        return repository.listFindBookingIncidents();
    }

    @Transactional
    public Map<String, Object> save(String token, long id, Map<String, Object> b) {
        ops.actor(token, "ADMIN");
        String status = Objects.toString(b.get("status"), "");
        if (!List.of("OPEN", "RESOLVED").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái sự cố không hợp lệ.");
        if (repository.saveUpdateBookingIncidents(status, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo sự cố.");
        return Map.of("id", id, "status", status);
    }
}
