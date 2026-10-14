package com.domesticservice.finance.controller;
import com.domesticservice.common.web.ServiceHttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance/admin")
public class FinanceStatisticsController {
    private final JdbcTemplate db;private final ServiceHttpClient identity;
    public FinanceStatisticsController(JdbcTemplate db,@Value("${clients.identity.base-url}")String url){this.db=db;identity=new ServiceHttpClient(url);}
    @GetMapping("/statistics")public Map<String,Object>stats(@RequestHeader(value="Authorization",required=false)String token,@RequestParam(defaultValue="2026")int year){identity.authenticate(token,"ADMIN");if(year<2020||year>2100)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Năm không hợp lệ.");var start=LocalDate.of(year,1,1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();var end=start.plusYears(1);return Map.of("revenue",db.queryForList("SELECT EXTRACT(MONTH FROM paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM payments WHERE status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') AND paid_at>=? AND paid_at<? GROUP BY 1 ORDER BY 1",start,end),"fees",db.queryForList("SELECT EXTRACT(MONTH FROM settled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(platform_fee) AS amount FROM staff_incomes WHERE status='SETTLED' AND settled_at>=? AND settled_at<? GROUP BY 1 ORDER BY 1",start,end),"payouts",db.queryForList("SELECT EXTRACT(MONTH FROM paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM withdrawals WHERE status='PAID' AND paid_at>=? AND paid_at<? GROUP BY 1 ORDER BY 1",start,end),"refunds",db.queryForList("SELECT EXTRACT(MONTH FROM completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,sum(amount) AS amount FROM refunds WHERE status='PROCESSED' AND completed_at>=? AND completed_at<? GROUP BY 1 ORDER BY 1",start,end));}
}
