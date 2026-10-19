package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingStatisticsSqlRepository {

    private final NativeQueryExecutor db;

    public BookingStatisticsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> statsCountCreatedAt(Object... args) {
        return db.queryForList("SELECT EXTRACT(MONTH FROM created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,count(*) AS bookings,count(*) FILTER(WHERE status='COMPLETED') AS completed,count(*) FILTER(WHERE status IN ('CANCELLED','REFUNDED','ABSENT','REJECTED')) AS cancelled FROM bookings WHERE created_at>=? AND created_at<? GROUP BY 1 ORDER BY 1", args);
    }

    public List<Map<String, Object>> statsCountBookings(Object... args) {
        return db.queryForList("SELECT coalesce(service_snapshot->>'name',service_snapshot->>'serviceName','Dịch vụ') AS name,count(*) AS value,sum(total_amount) AS amount FROM bookings WHERE created_at>=? AND created_at<? GROUP BY 1 ORDER BY value DESC", args);
    }

    public List<Map<String, Object>> statsCountBookings2(Object... args) {
        return db.queryForList("SELECT coalesce(address_snapshot->>'district','Chưa có khu vực') AS district,count(*) AS bookings FROM bookings WHERE created_at>=? AND created_at<? GROUP BY 1 ORDER BY bookings DESC LIMIT 10", args);
    }
}
