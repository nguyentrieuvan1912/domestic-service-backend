package com.domesticservice.booking.service;

import com.domesticservice.booking.service.BookingOperationsService;
import java.util.*;
import org.springframework.stereotype.Service;
import com.domesticservice.booking.repository.PublicStaffStatisticsSqlRepository;

@Service()
public class PublicStaffStatisticsService {

    private final PublicStaffStatisticsSqlRepository repository;

    private final BookingOperationsService ops;

    public PublicStaffStatisticsService(PublicStaffStatisticsSqlRepository repository, BookingOperationsService ops) {
        this.repository = repository;
        this.ops = ops;
    }

    public List<Map<String, Object>> stats() {
        var items = repository.statsCountBookingAssignments();
        // Catalog list is paginated, so use the complete service read model through the service helper.
        var names = ops.services(null);
        for (var item : items) {
            var capabilities = repository.statsFindStaffServiceCapabilities(Long.class, item.get("staffId"));
            item.put("competencies", capabilities.stream().map(names::get).filter(Objects::nonNull).map(service -> Objects.toString(service.get("name"), "")).toList());
        }
        return items;
    }

    public List<Map<String, Object>> reviews(long id) {
        return repository.reviewsFindReviews(id);
    }
}
