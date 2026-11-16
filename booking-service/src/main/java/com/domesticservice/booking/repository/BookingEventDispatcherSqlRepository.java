package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingEventDispatcherSqlRepository {

    private final NativeQueryExecutor db;

    public BookingEventDispatcherSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> dispatchFindOutboxEvents() {
        return db.queryForList("""
            SELECT e.id, (e.aggregate_type='BOOKING' AND e.event_type='MATCHING'
                AND e.payload->>'status'='MATCHING' AND b.booking_mode='MODE_B' AND b.status='MATCHING'
                AND b.starts_at>now() AND b.starts_at<=now()+interval '7 days') AS plan_needed
            FROM outbox_events e LEFT JOIN bookings b ON b.id=e.aggregate_id
            WHERE e.status IN ('PENDING','FAILED') AND e.attempts<20 AND e.event_type<>'PRICE_CONFIRMATION_PENDING'
                AND (e.event_type<>'MATCHING_BROADCAST' OR e.created_at<=now()-interval '1 minute')
            ORDER BY e.id LIMIT 10
            """);
    }

    public void queueMatchingBroadcast(long booking, String payload) {
        db.update("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'MATCHING_BROADCAST',CAST(? AS jsonb))",
            booking, payload);
    }

    public List<Map<String, Object>> dispatchFindOutboxEventsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM outbox_events WHERE id=? AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED", args);
    }

    public List<Map<String, Object>> dispatchFindBookings(Object... args) {
        return db.queryForList("""
            SELECT b.*, b.required_staff_count - (SELECT count(*) FROM booking_assignments a
                WHERE a.booking_id=b.id AND a.status IN ('ACCEPTED','COMPLETED')) AS remaining
            FROM bookings b WHERE b.id=?
            """, args);
    }

    public int dispatchUpdateOutboxEvents(Object... args) {
        return db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?", args);
    }

    public int dispatchUpdateOutboxEvents2(Object... args) {
        return db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?", args);
    }

    public <T> List<T> dispatchFindBookingAssignments(Class<T> type, Object... args) {
        return db.queryForList("SELECT staff_id FROM booking_assignments WHERE booking_id=? AND status IN ('ASSIGNED','ACCEPTED','COMPLETED','CANCELLED')", type, args);
    }

    public <T> List<T> dispatchFindStaffScheduleSettings(Class<T> type, Object... args) {
        return db.queryForList("SELECT match_notifications FROM staff_schedule_settings WHERE staff_id=?", type, args);
    }

    public int dispatchUpdateOutboxEvents3(Object... args) {
        return db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?", args);
    }

    public int dispatchUpdateOutboxEvents4(Object... args) {
        return db.update("UPDATE outbox_events SET status='FAILED',attempts=attempts+1 WHERE id=?", args);
    }
}
