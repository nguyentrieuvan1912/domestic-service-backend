package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class BookingWorkflowSqlRepository {

    private final NativeQueryExecutor db;

    public BookingWorkflowSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> rowFindBookingsForUpdate(boolean lock, Object... args) {
        return db.queryForList("SELECT * FROM bookings WHERE id=?" + (lock ? " FOR UPDATE" : ""), args);
    }

    public <T> T ownedExistsBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND (status IN ('ACCEPTED','COMPLETED') OR (status='CANCELLED' AND accepted_at IS NOT NULL)))", type, args);
    }

    public List<Map<String, Object>> bookingViewFindBookingAddOns(Object... args) {
        return db.queryForList("SELECT add_on_id AS \"addOnId\",name_snapshot AS \"addOnName\",unit_price AS price,quantity FROM booking_add_ons WHERE booking_id=? ORDER BY id", args);
    }

    public List<Map<String, Object>> bookingViewFindBookingAssignments(Object... args) {
        return db.queryForList("SELECT id,staff_id AS \"staffId\",status,staff_snapshot AS \"staffSnapshot\",execution_status AS \"executionStatus\",accepted_at AS \"acceptedAt\" FROM booking_assignments WHERE booking_id=? ORDER BY id", args);
    }

    public List<Map<String, Object>> bookingViewExistsBookingAssignments(Object... args) {
        return db.queryForList("SELECT staff_snapshot FROM booking_assignments WHERE booking_id=? AND (status IN ('ACCEPTED','COMPLETED') OR EXISTS(SELECT 1 FROM bookings b WHERE b.id=booking_assignments.booking_id AND b.booking_mode='MODE_A')) ORDER BY id DESC LIMIT 1", args);
    }

    public List<Map<String, Object>> listFindBookings(Map<String, Object> user) {
        String sql = "SELECT * FROM bookings";
        Object[] args = {};
        if ("CUSTOMER".equals(user.get("role"))) {
            sql += " WHERE customer_id=?";
            args = new Object[] { user.get("id") };
        } else if ("STAFF".equals(user.get("role"))) {
            sql += " WHERE id IN(SELECT booking_id FROM booking_assignments WHERE staff_id=? AND (status IN ('ACCEPTED','COMPLETED') OR (status='CANCELLED' AND accepted_at IS NOT NULL)))";
            args = new Object[] { user.get("id") };
        }
        return db.queryForList(sql + " ORDER BY created_at DESC LIMIT 500", args);
    }

    public List<Map<String, Object>> historyFindBookingStatusHistories(Object... args) {
        return db.queryForList("SELECT id,new_status AS status,reason AS description,changed_at AS timestamp,changed_by_type AS \"changedBy\" FROM booking_status_histories WHERE booking_id=? ORDER BY changed_at,id", args);
    }

    public <T> T eligibilityExistsStaffServiceCapabilities(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_service_capabilities WHERE staff_id=? AND service_id=? AND status='APPROVED' AND approved_at<=now() AND (valid_from IS NULL OR valid_from<=?) AND (valid_until IS NULL OR valid_until>=?))", type, args);
    }

    public <T> T eligibilityExistsStaffRestrictions(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_restrictions WHERE staff_id=? AND status='ACTIVE' AND restriction_type IN ('SERVICE_LIMIT','SUSPENSION') AND (service_id IS NULL OR service_id=?) AND starts_at<? AND (ends_at IS NULL OR ends_at>?))", type, args);
    }

    public <T> List<T> eligibilityFindStaffAreas(Class<T> type, Object... args) {
        return db.queryForList("SELECT province FROM staff_areas WHERE staff_id=? AND status='ACTIVE' AND COALESCE(NULLIF(ward,''),district)=?", type, args);
    }

    public <T> T eligibilityExistsStaffAvailabilities(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_availabilities WHERE staff_id=? AND status='AVAILABLE' AND (available_date=? OR (available_date IS NULL AND day_of_week=?)) AND start_time<=? AND end_time>=?)", type, args);
    }

    public <T> T eligibilityExistsBookings(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.staff_id=? AND a.status IN ('ASSIGNED','ACCEPTED','COMPLETED') AND b.id<>? AND b.status NOT IN ('CANCELLED','REFUNDED','REJECTED') AND b.starts_at<? AND b.ends_at>?)", type, args);
    }

    public <T> T eligibilityCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status IN ('ACCEPTED','COMPLETED')", type, args);
    }

    public <T> T eligibilityExistsBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status='ASSIGNED')", type, args);
    }

    public <T> T matchItemCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status IN ('ACCEPTED','COMPLETED')", type, args);
    }

    public List<Map<String, Object>> matchingExistsBookings(Object... args) {
        return db.queryForList("SELECT * FROM bookings b WHERE b.pricing_confirmed=true AND b.status IN ('PENDING','MATCHING','STAFF_ASSIGNED') AND b.starts_at>now() AND (b.booking_mode<>'MODE_A' OR (b.staff_wait_state='WAITING' AND b.staff_response_deadline>now())) AND (b.booking_mode='MODE_B' OR EXISTS(SELECT 1 FROM booking_assignments a WHERE a.booking_id=b.id AND a.staff_id=? AND a.status='ASSIGNED')) AND NOT EXISTS(SELECT 1 FROM booking_assignments mine WHERE mine.booking_id=b.id AND mine.staff_id=? AND mine.status IN ('ACCEPTED','COMPLETED','REJECTED')) ORDER BY b.starts_at", args);
    }

    public <T> T opportunityDetailExistsBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status='ASSIGNED')", type, args);
    }

    public List<Map<String, Object>> assignmentViewFindBookingAssignments(Object... args) {
        return db.queryForList("SELECT * FROM booking_assignments WHERE staff_id=? AND booking_id=? AND (status IN ('ACCEPTED','COMPLETED') OR (status='CANCELLED' AND (accepted_at IS NOT NULL OR cancellation_reason='Hết thời gian nhận đơn'))) ORDER BY id DESC LIMIT 1", args);
    }

    public <T> List<T> assignmentViewFindConversations(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM conversations WHERE booking_id=? AND staff_id=?", type, args);
    }

    public List<Map<String, Object>> assignmentsFindBookings(Object... args) {
        return db.queryForList("SELECT * FROM bookings WHERE id IN (SELECT booking_id FROM booking_assignments WHERE staff_id=? AND (status IN ('ACCEPTED','COMPLETED') OR (status='CANCELLED' AND (accepted_at IS NOT NULL OR cancellation_reason='Hết thời gian nhận đơn')))) ORDER BY starts_at DESC", args);
    }

    public <T> T claimExistsBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status IN ('ACCEPTED','COMPLETED'))", type, args);
    }

    public int claimUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET status='ACCEPTED',accepted_at=now(),execution_status='ACCEPTED' WHERE booking_id=? AND staff_id=? AND status='ASSIGNED' AND EXISTS(SELECT 1 FROM bookings b WHERE b.id=booking_assignments.booking_id AND b.staff_response_deadline>clock_timestamp())", args);
    }

    public long claimInsertBookingAssignments(Object... args) {
        return insert("INSERT INTO booking_assignments(booking_id,staff_id,status,accepted_at,execution_status) VALUES (?,?,'ACCEPTED',now(),'ACCEPTED')", args);
    }

    public int claimUpdateBookingAssignments2(Object... args) {
        return db.update("UPDATE booking_assignments SET checklist=CAST(? AS jsonb) WHERE booking_id=? AND staff_id=? AND status='ACCEPTED'", args);
    }

    public Map<String, Object> claimCountReviews(Object... args) {
        return db.queryForMap("SELECT coalesce(avg(rating),5) AS rating,count(*) AS reviews FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED')", args);
    }

    public int claimUpdateBookingAssignments3(Object... args) {
        return db.update("UPDATE booking_assignments SET staff_snapshot=CAST(? AS jsonb) WHERE booking_id=? AND staff_id=? AND status='ACCEPTED'", args);
    }

    public int claimInsertStaffReservations(Object... args) {
        return db.update("INSERT INTO staff_reservations(staff_id,booking_id,starts_at,ends_at,status) VALUES (?,?,?,?,'CONFIRMED')", args);
    }

    public int claimInsertConversations(Object... args) {
        return db.update("INSERT INTO conversations(booking_id,customer_id,staff_id) VALUES (?,?,?) ON CONFLICT(booking_id,staff_id) DO NOTHING", args);
    }

    public int claimUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET staff_wait_state=CASE WHEN staff_wait_state IS NULL THEN NULL ELSE 'CLOSED' END WHERE id=?", args);
    }

    public <T> T claimCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status='ACCEPTED'", type, args);
    }

    public int transitionUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET status=?,updated_at=now(),version=version+1 WHERE id=?", args);
    }

    public long transitionInsertBookingStatusHistories(Object... args) {
        return insert("INSERT INTO booking_status_histories(booking_id,old_status,new_status,changed_by,changed_by_type,reason) VALUES (?,?,?,?,?,?)", args);
    }

    public long transitionInsertOutboxEvents(Object... args) {
        return insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,?,CAST(? AS jsonb))", args);
    }

    public List<Map<String, Object>> progressFindBookingAssignmentsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status IN ('ACCEPTED','COMPLETED') FOR UPDATE", args);
    }

    public <T> T progressCountBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status IN ('ACCEPTED','COMPLETED')", type, args);
    }

    public int progressUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET execution_status=?,status=? WHERE id=?", args);
    }

    public int progressUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET started_at=COALESCE(started_at,now()) WHERE id=?", args);
    }

    public int progressUpdateStaffReservations(Object... args) {
        return db.update("UPDATE staff_reservations SET status='IN_PROGRESS' WHERE booking_id=? AND staff_id=? AND status='CONFIRMED'", args);
    }

    public int progressUpdateStaffReservations2(Object... args) {
        return db.update("UPDATE staff_reservations SET status='COMPLETED' WHERE booking_id=? AND staff_id=?", args);
    }

    public <T> T progressCountBookingAssignments2(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status='COMPLETED'", type, args);
    }

    public int progressUpdateBookings2(Object... args) {
        return db.update("UPDATE bookings SET completed_at=now() WHERE id=?", args);
    }

    public int progressUpdateConversations(Object... args) {
        return db.update("UPDATE conversations SET status='CLOSED',closed_at=now() WHERE booking_id=?", args);
    }

    public int progressUpdateStaffReservations3(Object... args) {
        return db.update("UPDATE staff_reservations SET status='CANCELLED' WHERE booking_id=? AND staff_id=?", args);
    }

    public int progressUpdateConversations2(Object... args) {
        return db.update("UPDATE conversations SET status='CLOSED',closed_at=now() WHERE booking_id=? AND staff_id=?", args);
    }

    public long progressInsertBookingIncidents(Object... args) {
        return insert("INSERT INTO booking_incidents(booking_id,reporter_id,reporter_role,reason) VALUES (?,?,'STAFF',?)", args);
    }

    public List<Map<String, Object>> checklistFindBookingAssignments(Object... args) {
        return db.queryForList("SELECT id,checklist FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status='ACCEPTED'", args);
    }

    public int checklistUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET checklist=CAST(? AS jsonb) WHERE id=?", args);
    }

    public int cancelUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET cancel_reason=?,cancelled_by=?,cancelled_by_type=?,cancelled_at=now() WHERE id=?", args);
    }

    public int cancelUpdateBookings2(Object... args) {
        return db.update("UPDATE bookings SET staff_wait_state=CASE WHEN staff_wait_state IS NULL THEN NULL ELSE 'CLOSED' END WHERE id=?", args);
    }

    public int cancelUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET status='CANCELLED',execution_status='CANCELLED',cancellation_reason=? WHERE booking_id=? AND status IN ('ASSIGNED','ACCEPTED')", args);
    }

    public int cancelUpdateStaffReservations(Object... args) {
        return db.update("UPDATE staff_reservations SET status='CANCELLED' WHERE booking_id=? AND status IN ('HELD','CONFIRMED')", args);
    }

    public int cancelUpdateConversations(Object... args) {
        return db.update("UPDATE conversations SET status='CLOSED',closed_at=now() WHERE booking_id=?", args);
    }

    public List<Map<String, Object>> reviewsFindReviews(Map<String, Object> user) {
        String filter = "ADMIN".equals(user.get("role")) ? "" : "STAFF".equals(user.get("role")) ? " WHERE staff_id=? AND moderation_status<>'HIDDEN'" : " WHERE customer_id=?";
        Object[] args = filter.isEmpty() ? new Object[] {} : new Object[] { user.get("id") };
        return db.queryForList("SELECT id,booking_id AS \"bookingId\",customer_id AS \"customerId\",staff_id AS \"staffId\",service_id AS \"serviceId\",rating,comment,tags::text AS tags,moderation_status AS status,internal_note AS \"internalNote\",created_at AS \"createdAt\" FROM reviews" + filter + " ORDER BY created_at DESC", args);
    }

    public int moderateReviewUpdateReviews(Object... args) {
        return db.update("UPDATE reviews SET moderation_status=?,internal_note=?,updated_at=now() WHERE id=?", args);
    }

    public long incidentInsertBookingIncidents(Object... args) {
        return insert("INSERT INTO booking_incidents(booking_id,reporter_id,reporter_role,reason) VALUES (?,?,?,?)", args);
    }

    public int rejectUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET status='REJECTED',rejected_at=now() WHERE booking_id=? AND staff_id=? AND status='ASSIGNED'", args);
    }

    public <T> T createReviewExistsBookingAssignments(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status='COMPLETED')", type, args);
    }

    public List<Map<String, Object>> createReviewFindReviews(Object... args) {
        return db.queryForList("SELECT id,rating,comment,tags FROM reviews WHERE booking_id=? AND staff_id=?", args);
    }

    public long createReviewInsertReviews(Object... args) {
        return insert("INSERT INTO reviews(booking_id,customer_id,staff_id,service_id,rating,comment,tags) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))", args);
    }

    public List<Map<String, Object>> publicReviewsFindReviews(Object... args) {
        return db.queryForList("SELECT id,rating,comment,created_at AS \"createdAt\" FROM reviews WHERE service_id=? AND moderation_status IN ('PUBLISHED','RESOLVED') ORDER BY created_at DESC LIMIT 100", args);
    }

    public List<Map<String, Object>> publicReviewStatsCountReviews(Object... args) {
        return db.queryForList("SELECT service_id AS \"serviceId\",count(*) AS \"reviewCount\",avg(rating) AS rating FROM reviews WHERE moderation_status IN ('PUBLISHED','RESOLVED') GROUP BY service_id", args);
    }
}
