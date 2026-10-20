package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class StaffResponseSqlRepository {

    private final NativeQueryExecutor db;

    public StaffResponseSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> List<T> expireInvitationsFindBookings(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM bookings WHERE staff_wait_state='WAITING' AND status='STAFF_ASSIGNED' AND staff_response_deadline<=now() ORDER BY staff_response_deadline LIMIT 50", type, args);
    }

    public List<Map<String, Object>> expireInvitationsFindBookingsForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM bookings WHERE id=? FOR UPDATE SKIP LOCKED", args);
    }

    public int expireInvitationsUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET staff_wait_state='CLOSED' WHERE staff_wait_state='EXPIRED' AND starts_at<=now()", args);
    }

    public int expireUpdateBookingAssignments(Object... args) {
        return db.update("UPDATE booking_assignments SET status='CANCELLED',execution_status='CANCELLED',cancellation_reason=? WHERE booking_id=? AND status='ASSIGNED'", args);
    }

    public int expireUpdateStaffReservations(Object... args) {
        return db.update("UPDATE staff_reservations SET status='CANCELLED' WHERE booking_id=? AND status='HELD'", args);
    }

    public int expireUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET staff_wait_state='EXPIRED',cancel_reason=?,cancelled_by_type='SYSTEM',cancelled_at=now() WHERE id=?", args);
    }

    public List<Map<String, Object>> decideFindBookingAssignments(Object... args) {
        return db.queryForList("SELECT a.* FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id WHERE b.id=? AND b.customer_id=? AND b.booking_mode='MODE_A' ORDER BY a.id DESC LIMIT 1", args);
    }

    public Map<String, Object> decideFindBookingsForUpdate(Object... args) {
        return db.queryForMap("SELECT * FROM bookings WHERE id=? AND customer_id=? FOR UPDATE", args);
    }

    public Map<String, Object> decideFindBookings(Object... args) {
        return db.queryForMap("SELECT * FROM bookings WHERE id=?", args);
    }

    public int decideUpdateBookings(Object... args) {
        return db.update("UPDATE bookings SET staff_wait_state='CLOSED',cancel_reason=?,cancelled_by=?,cancelled_by_type='CUSTOMER' WHERE id=?", args);
    }

    public long decideInsertBookingAssignments(Object... args) {
        return insert("INSERT INTO booking_assignments(booking_id,staff_id,status,staff_snapshot) VALUES (?,?,'ASSIGNED',CAST(? AS jsonb))", args);
    }

    public int decideUpdateBookings2(Object... args) {
        return db.update("UPDATE bookings SET staff_response_deadline=clock_timestamp()+interval '5 minutes',staff_wait_state='WAITING',staff_wait_round=staff_wait_round+1,cancel_reason=NULL,cancelled_at=NULL,cancelled_by=NULL,cancelled_by_type=NULL WHERE id=?", args);
    }

    public Map<String, Object> decideFindBookings2(Object... args) {
        return db.queryForMap("SELECT * FROM bookings WHERE id=?", args);
    }
}
