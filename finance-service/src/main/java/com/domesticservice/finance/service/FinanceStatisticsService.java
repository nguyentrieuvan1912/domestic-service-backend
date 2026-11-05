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

    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> details(String token,int year) {
        identity.authenticate(token,"ADMIN");
        if(year<2020 || year>2100) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Năm không hợp lệ.");
        var start=LocalDate.of(year,1,1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        var rows=new ArrayList<Map<String,Object>>();
        for(var source:repository.reportRecords(start,start.plusYears(1))) {
            Map<String,Object> row=new LinkedHashMap<>();
            for(String key:List.of("kind","id","bookingId","actorId","amount","status","effectiveAt","method","reference","description","serviceAmount","staffAmount","tipAmount"))
                if(source.containsKey(key)) row.put(key,source.get(key));
            Object date=row.get("effectiveAt");
            if(date instanceof java.sql.Timestamp time) row.put("effectiveAt",time.toInstant().toString());
            else if(date instanceof OffsetDateTime time) row.put("effectiveAt",time.toInstant().toString());
            rows.add(row);
        }
        return rows;
    }
}
