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

    public <T> List<T> dispatchFindOutboxEvents(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM outbox_events WHERE status IN ('PENDING','FAILED') AND attempts<20 AND event_type<>'PRICE_CONFIRMATION_PENDING' ORDER BY id LIMIT 10", type, args);
    }

    public List<Map<String, Object>> dispatchFindOutboxEventsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM outbox_events WHERE id=? AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED", args);
    }

    public List<Map<String, Object>> dispatchFindBookings(Object... args) {
        return db.queryForList("SELECT * FROM bookings WHERE id=?", args);
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
