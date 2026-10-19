package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingPaymentVerificationSqlRepository {

    private final NativeQueryExecutor db;

    public BookingPaymentVerificationSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> verifyFindBookingsForUpdate(Object... args) {
        return db.queryForList("SELECT total_amount,payment_status FROM bookings WHERE id=? FOR UPDATE", args);
    }

    public int verifyUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET payment_status=?,updated_at=now() WHERE id=?", args);
    }
}
