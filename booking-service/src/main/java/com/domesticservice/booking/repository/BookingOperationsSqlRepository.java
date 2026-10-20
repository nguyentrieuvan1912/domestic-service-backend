package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingOperationsSqlRepository {

    private final NativeQueryExecutor db;

    public BookingOperationsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public int lockStaffInsertStaffScheduleSettings(Object... args) {
        return db.update("INSERT INTO staff_schedule_settings(staff_id) VALUES (?) ON CONFLICT(staff_id) DO NOTHING", args);
    }

    public <T> T lockStaffFindStaffScheduleSettingsForUpdate(Class<T> type, Object... args) {
        return db.queryForObject("SELECT staff_id FROM staff_schedule_settings WHERE staff_id=? FOR UPDATE", type, args);
    }

    public List<Map<String, Object>> capabilitiesFindStaffServiceCapabilities(Object... args) {
        return db.queryForList("SELECT * FROM staff_service_capabilities WHERE staff_id=? ORDER BY id", args);
    }

    public List<Map<String, Object>> restrictionsFindStaffRestrictions(Object... args) {
        return db.queryForList("SELECT id,staff_id AS \"staffId\",restriction_type AS type,service_id AS \"serviceId\",reason,starts_at AS \"startsAt\",ends_at AS \"endsAt\",status FROM staff_restrictions WHERE staff_id=? ORDER BY id", args);
    }

    public List<Map<String, Object>> scheduleFindStaffScheduleSettings(Object... args) {
        return db.queryForList("SELECT * FROM staff_schedule_settings WHERE staff_id=?", args);
    }

    public List<Map<String, Object>> scheduleFindStaffAvailabilities(Object... args) {
        return db.queryForList("SELECT * FROM staff_availabilities WHERE staff_id=? AND status='AVAILABLE' ORDER BY available_date,start_time", args);
    }

    public <T> List<T> scheduleFindStaffAreas(Class<T> type, Object... args) {
        return db.queryForList("SELECT COALESCE(NULLIF(ward,''),district) FROM staff_areas WHERE staff_id=? AND status='ACTIVE' ORDER BY id", type, args);
    }

    public <T> List<T> scheduleFindStaffScheduleWeeks(Class<T> type, Object... args) {
        return db.queryForList("SELECT week_start::text FROM staff_schedule_weeks WHERE staff_id=? ORDER BY week_start", type, args);
    }

    public List<Map<String, Object>> scheduleFindBookings(Object... args) {
        return db.queryForList("SELECT b.id,b.booking_code AS \"bookingCode\",b.starts_at AS \"startsAt\",b.ends_at AS \"endsAt\",COALESCE(NULLIF(b.address_snapshot->>'ward',''),b.address_snapshot->>'district') AS district,b.status FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.staff_id=? AND a.status IN ('ASSIGNED','ACCEPTED','COMPLETED') AND b.status NOT IN ('CANCELLED','REFUNDED','REJECTED') ORDER BY b.starts_at", args);
    }

    public <T> T saveScheduleFindStaffScheduleSettings(Class<T> type, Object... args) {
        return db.queryForObject("SELECT revision FROM staff_schedule_settings WHERE staff_id=?", type, args);
    }

    public List<Map<String, Object>> saveScheduleFindStaffAvailabilities(Object... args) {
        return db.queryForList("SELECT available_date,start_time,end_time FROM staff_availabilities WHERE staff_id=? AND status='AVAILABLE' AND available_date BETWEEN ? AND ?", args);
    }

    public List<Map<String, Object>> saveScheduleFindBookings(Object... args) {
        return db.queryForList("SELECT b.starts_at,b.ends_at,COALESCE(NULLIF(b.address_snapshot->>'ward',''),b.address_snapshot->>'district') district FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.staff_id=? AND a.status IN ('ASSIGNED','ACCEPTED') AND b.ends_at>now() AND b.status NOT IN ('CANCELLED','REFUNDED','REJECTED')", args);
    }

    public int saveScheduleDeleteStaffAvailabilities(Object... args) {
        return db.update("DELETE FROM staff_availabilities WHERE staff_id=? AND available_date BETWEEN ? AND ?", args);
    }

    public int saveScheduleInsertStaffAvailabilities(Object... args) {
        return db.update("INSERT INTO staff_availabilities(staff_id,available_date,start_time,end_time) VALUES (?,?,?,?)", args);
    }

    public int saveScheduleInsertStaffScheduleWeeks(Object... args) {
        return db.update("INSERT INTO staff_schedule_weeks(staff_id,week_start) VALUES (?,?) ON CONFLICT(staff_id,week_start) DO UPDATE SET registered_at=now()", args);
    }

    public int saveScheduleUpdateStaffScheduleSettings(Object... args) {
        return db.update("UPDATE staff_schedule_settings SET revision=revision+1,match_notifications=?,updated_at=now() WHERE staff_id=?", args);
    }

    public <T> T weekRegisteredExistsStaffScheduleWeeks(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_schedule_weeks WHERE staff_id=? AND week_start=?)", type, args);
    }

    public int saveAreasDeleteStaffAreas(Object... args) {
        return db.update("DELETE FROM staff_areas WHERE staff_id=?", args);
    }

    public int saveAreasInsertStaffAreas(Object... args) {
        return db.update("INSERT INTO staff_areas(staff_id,province,ward,is_primary) VALUES (?,'TP. Hồ Chí Minh',?,?)", args);
    }

    public <T> T staffOperationsCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE staff_id=? AND status='COMPLETED'", type, args);
    }

    public <T> T staffOperationsCountBookingAssignments2(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE staff_id=? AND status='CANCELLED'", type, args);
    }

    public <T> T staffOperationsFindReviews(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(avg(rating),0) FROM reviews WHERE staff_id=? AND moderation_status<>'HIDDEN'", type, args);
    }

    public <T> T staffOperationsCountReviews(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM reviews WHERE staff_id=? AND moderation_status<>'HIDDEN'", type, args);
    }

    public <T> T staffPeriodOperationsCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id WHERE a.staff_id=? AND a.status='COMPLETED' AND b.starts_at>=? AND b.starts_at<?", type, args);
    }

    public <T> T staffPeriodOperationsCountBookingAssignments2(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id WHERE a.staff_id=? AND a.status='CANCELLED' AND b.starts_at>=? AND b.starts_at<?", type, args);
    }

    public <T> T staffPeriodOperationsFindReviews(Class<T> type, Object... args) {
        return db.queryForObject("SELECT coalesce(avg(rating),0) FROM reviews WHERE staff_id=? AND moderation_status<>'HIDDEN' AND created_at>=? AND created_at<?", type, args);
    }

    public <T> T staffPeriodOperationsCountReviews(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM reviews WHERE staff_id=? AND moderation_status<>'HIDDEN' AND created_at>=? AND created_at<?", type, args);
    }

    public int saveStaffOperationsUpdateStaffServiceCapabilities(Object... args) {
        return db.update("UPDATE staff_service_capabilities SET status='INACTIVE' WHERE staff_id=?", args);
    }

    public int saveStaffOperationsInsertStaffServiceCapabilities(Object... args) {
        return db.update("INSERT INTO staff_service_capabilities(staff_id,service_id,skill_level,status,approved_at,approved_by,verified_at,verified_by) VALUES (?,?,'INTERMEDIATE','APPROVED',now(),?,now(),?) ON CONFLICT(staff_id,service_id) DO UPDATE SET status='APPROVED',approved_at=now(),approved_by=EXCLUDED.approved_by,verified_at=now(),verified_by=EXCLUDED.verified_by", args);
    }

    public long saveStaffOperationsInsertStaffRestrictions(Object... args) {
        return insert("INSERT INTO staff_restrictions(staff_id,restriction_type,service_id,reason,starts_at,ends_at,status,created_by) VALUES (?,?,?,?,?,?,?,?)", args);
    }

    public int saveStaffOperationsUpdateStaffRestrictions(Object... args) {
        return db.update("UPDATE staff_restrictions SET restriction_type=?,reason=?,starts_at=?,ends_at=?,status=? WHERE id=? AND staff_id=?", args);
    }

    public int saveStaffOperationsUpdateStaffScheduleSettings(Object... args) {
        return db.update("UPDATE staff_schedule_settings SET revision=revision+1,updated_at=now() WHERE staff_id=?", args);
    }
}
