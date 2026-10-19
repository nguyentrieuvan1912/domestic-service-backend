package com.domesticservice.booking.service;

import com.domesticservice.booking.service.BookingOperationsService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.domesticservice.booking.repository.BookingStatisticsSqlRepository;

@Service()
public class BookingStatisticsService {

    private final BookingOperationsService ops;

    private final BookingStatisticsSqlRepository repository;

    public BookingStatisticsService(BookingOperationsService ops, BookingStatisticsSqlRepository repository) {
        this.ops = ops;
        this.repository = repository;
    }

    public Map<String, Object> stats(String token, int year, String period) {
        ops.actor(token, "ADMIN");
        if (year < 2020 || year > 2100)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Năm không hợp lệ.");
        var start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        var end = start.plusYears(1);
        int month = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getMonthValue();
        int first = period.equals("month") ? month : period.matches("q[1-4]") ? (Integer.parseInt(period.substring(1)) - 1) * 3 + 1 : 1;
        int length = period.equals("month") ? 1 : period.matches("q[1-4]") ? 3 : 12;
        if (!period.equals("year") && !period.equals("month") && !period.matches("q[1-4]"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Kỳ không hợp lệ.");
        var shareStart = start.plusMonths(first - 1);
        var shareEnd = shareStart.plusMonths(length);
        return Map.of("monthly", repository.statsCountCreatedAt(start, end), "serviceShares", repository.statsCountBookings(shareStart, shareEnd), "districts", repository.statsCountBookings2(shareStart, shareEnd));
    }
}
