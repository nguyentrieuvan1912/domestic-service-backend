package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingIncidentSqlRepository {

    private final NativeQueryExecutor db;

    public BookingIncidentSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> listFindBookingIncidents(Object... args) {
        return db.queryForList("SELECT i.id,i.booking_id AS \"bookingId\",b.booking_code AS \"bookingCode\",i.reporter_role AS \"reporterRole\",i.reason,i.status,i.created_at AS \"createdAt\" FROM booking_incidents i JOIN bookings b ON b.id=i.booking_id ORDER BY i.id DESC LIMIT 500", args);
    }

    public int saveUpdateBookingIncidents(Object... args) {
        return db.update("UPDATE booking_incidents SET status=? WHERE id=?", args);
    }
}
