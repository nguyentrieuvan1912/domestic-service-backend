package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingCreationSqlRepository {

    private final NativeQueryExecutor db;

    public BookingCreationSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> createFindBookings(Object... args) {
        return db.queryForList("SELECT id,request_hash,pricing_confirmed FROM bookings WHERE customer_id=? AND request_key=?", args);
    }

    public void createLockRecords(long customer, Object... args) {
        db.execute("SELECT pg_advisory_xact_lock(" + customer + ")");
    }

    public List<Map<String, Object>> createFindBookings2(Object... args) {
        return db.queryForList("SELECT id,request_hash FROM bookings WHERE customer_id=? AND request_key=?", args);
    }

    public long createInsertBookings(Object... args) {
        return insert("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,package_id,address_id,quote_id,quote_version,service_snapshot,address_snapshot,starts_at,ends_at,required_staff_count,base_amount,add_on_amount,discount_amount,total_amount,promotion_id,promotion_code,payment_method,notes,request_key,request_hash,pricing_confirmed) VALUES (?,?,'PENDING',?,?,?,?,?,1,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,'CASH',?,?,?,false)", args);
    }

    public long createInsertBookingAddOns(Object... args) {
        return insert("INSERT INTO booking_add_ons(booking_id,add_on_id,name_snapshot,quantity,unit_price,extra_duration_minutes,total_price) VALUES (?,?,?,1,?,?,?)", args);
    }

    public long createInsertBookingAssignments(Object... args) {
        return insert("INSERT INTO booking_assignments(booking_id,staff_id,status,staff_snapshot) VALUES (?,?,'ASSIGNED',CAST(? AS jsonb))", args);
    }

    public long createInsertBookingStatusHistories(Object... args) {
        return insert("INSERT INTO booking_status_histories(booking_id,new_status,changed_by,changed_by_type,reason) VALUES (?,'PENDING',?,'CUSTOMER','Đang xác nhận báo giá chính thức.')", args);
    }

    public long createInsertOutboxEvents(Object... args) {
        return insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'PRICE_CONFIRMATION_PENDING',CAST(? AS jsonb))", args);
    }

    public Map<String, Object> createFindBookingsForUpdate(Object... args) {
        return db.queryForMap("SELECT * FROM bookings WHERE id=? FOR UPDATE", args);
    }

    public int createUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET pricing_confirmed=true,staff_response_deadline=CASE WHEN booking_mode='MODE_A' THEN now()+interval '5 minutes' END,staff_wait_state=CASE WHEN booking_mode='MODE_A' THEN 'WAITING' END,staff_wait_round=CASE WHEN booking_mode='MODE_A' THEN 1 ELSE 0 END WHERE id=?", args);
    }

    public int createUpdateOutboxEvents(Object... args) {
        return db.update("UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE aggregate_id=? AND event_type='PRICE_CONFIRMATION_PENDING'", args);
    }

    public <T> List<T> staffSearchFindStaffServiceCapabilities(Class<T> type, Object... args) {
        return db.queryForList("SELECT service_id FROM staff_service_capabilities WHERE staff_id=? AND status='APPROVED' AND approved_at<=now() AND (valid_from IS NULL OR valid_from<=?) AND (valid_until IS NULL OR valid_until>=?) ORDER BY service_id", type, args);
    }

    public <T> List<T> staffSearchFindStaffAreas(Class<T> type, Object... args) {
        return db.queryForList("SELECT COALESCE(NULLIF(ward,''),district) FROM staff_areas WHERE staff_id=? AND status='ACTIVE' ORDER BY is_primary DESC,id", type, args);
    }

    public Map<String, Object> staffSearchCountReviews(Object... args) {
        return db.queryForMap("SELECT coalesce(avg(rating),5) AS rating,count(*) AS reviews FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED')", args);
    }

    public <T> T staffSearchCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE staff_id=? AND status='COMPLETED'", type, args);
    }

    public <T> List<T> availabilityLimitFindStaffAvailabilities(Class<T> type, Object... args) {
        return db.queryForList("SELECT end_time FROM staff_availabilities WHERE staff_id=? AND status='AVAILABLE' AND (available_date=? OR (available_date IS NULL AND day_of_week=?)) AND start_time<=? AND end_time>?", type, args);
    }

    public <T> T availabilityLimitFindBookings(Class<T> type, Object... args) {
        return db.queryForObject("SELECT min(b.starts_at) FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.staff_id=? AND a.status IN ('ASSIGNED','ACCEPTED','COMPLETED') AND b.status NOT IN ('CANCELLED','REFUNDED','REJECTED','NO_STAFF_FOUND') AND b.starts_at>?", type, args);
    }
}
