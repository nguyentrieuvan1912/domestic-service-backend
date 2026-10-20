package com.domesticservice.booking.repository;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class BookingPushValidityRepository {
    private final NativeQueryExecutor db;
    public BookingPushValidityRepository(NativeQueryExecutor db){this.db=db;}
    public boolean valid(Map<String,Object> data,long user){
        if(data.get("callId") instanceof Number id)return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_calls k JOIN conversations c ON c.id=k.conversation_id JOIN bookings b ON b.id=c.booking_id JOIN booking_assignments a ON a.booking_id=b.id AND a.staff_id=c.staff_id AND a.status='ACCEPTED' WHERE k.id=? AND k.callee_id=? AND k.status='RINGING' AND k.created_at>now()-interval '60 seconds' AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED','IN_PROGRESS'))",Boolean.class,id.longValue(),user);
        if(data.get("assignmentId") instanceof Number id && data.get("startsAt") instanceof String starts)return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id WHERE a.id=? AND a.staff_id=? AND a.status='ACCEPTED' AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED') AND b.starts_at=CAST(? AS timestamptz) AND b.starts_at>now())",Boolean.class,id.longValue(),user,starts);
        return false;
    }
}
