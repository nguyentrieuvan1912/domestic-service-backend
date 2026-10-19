package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.domesticservice.finance.repository.FinanceStatisticsSqlRepository;

@Service()
public class FinanceStatisticsService {

    private final FinanceStatisticsSqlRepository repository;

    private final ServiceHttpClient identity;

    public FinanceStatisticsService(FinanceStatisticsSqlRepository repository, @Value("${clients.identity.base-url}") String url) {
        this.repository = repository;
        identity = new ServiceHttpClient(url);
    }

    public Map<String, Object> stats(String token, int year) {
        identity.authenticate(token, "ADMIN");
        if (year < 2020 || year > 2100)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Năm không hợp lệ.");
        var start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        var end = start.plusYears(1);
        return Map.of("revenue", repository.statsFindPaidAt(start, end), "fees", repository.statsFindSettledAt(start, end), "payouts", repository.statsFindPaidAt2(start, end), "refunds", repository.statsFindCompletedAt(start, end));
    }
}
