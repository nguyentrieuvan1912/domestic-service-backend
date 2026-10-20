package com.domesticservice.booking.repository;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class BookingReminderRepository {
    private final NativeQueryExecutor db;
    public BookingReminderRepository(NativeQueryExecutor db){this.db=db;}
    public List<Long> candidates(){return db.queryForList("SELECT DISTINCT b.id FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id WHERE a.status='ACCEPTED' AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED') AND b.starts_at>now() AND b.starts_at<=now()+interval '60 minutes' AND NOT EXISTS(SELECT 1 FROM booking_reminder_deliveries d WHERE d.assignment_id=a.id AND d.starts_at=b.starts_at AND d.minutes_before=CASE WHEN b.starts_at>now()+interval '30 minutes' THEN 60 ELSE 30 END) ORDER BY b.id LIMIT 100",Long.class);}
    public List<Map<String,Object>> due(long id){return db.queryForList("""
        SELECT b.id,b.booking_code,b.starts_at,a.id AS assignment_id,a.staff_id,
         CASE WHEN b.starts_at>now()+interval '30 minutes' THEN 60 ELSE 30 END AS minutes
        FROM bookings b JOIN booking_assignments a ON a.booking_id=b.id
        WHERE b.id=? AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED') AND a.status='ACCEPTED'
         AND b.starts_at>now() AND b.starts_at<=now()+interval '60 minutes'
        FOR UPDATE OF b,a SKIP LOCKED
        """,id);}
    public boolean claim(Map<String,Object> row){return db.update("INSERT INTO booking_reminder_deliveries(assignment_id,starts_at,minutes_before) VALUES (?,?,?) ON CONFLICT DO NOTHING",row.get("assignment_id"),row.get("starts_at"),row.get("minutes"))>0;}
}
