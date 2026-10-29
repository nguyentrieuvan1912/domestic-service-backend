package com.domesticservice.booking.repository;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class BookingCallRepository {
    private final NativeQueryExecutor db;
    public BookingCallRepository(NativeQueryExecutor db){this.db=db;}
    public Map<String,Object> conversation(long id,long user,boolean lock){
        var rows=db.queryForList("SELECT c.*,b.status AS booking_status FROM conversations c JOIN bookings b ON b.id=c.booking_id WHERE c.id=? AND (c.customer_id=? OR c.staff_id=?)"+(lock?" FOR UPDATE OF b,c":""),id,user,user);
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy hội thoại của tài khoản này.");return rows.getFirst();
    }
    public boolean eligible(Map<String,Object> c){return "ACTIVE".equals(c.get("status")) && List.of("CONFIRMED","ACCEPTED","STAFF_ASSIGNED","IN_PROGRESS").contains(c.get("booking_status")) && db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status='ACCEPTED')",Boolean.class,c.get("booking_id"),c.get("staff_id"));}
    public Map<String,Object> find(long id,boolean lock){var rows=db.queryForList("SELECT * FROM booking_calls WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy cuộc gọi.");return rows.getFirst();}
    public List<Map<String,Object>> existing(long user,String key){return db.queryForList("SELECT * FROM booking_calls WHERE caller_id=? AND request_key=?",user,key);}
    public List<Map<String,Object>> active(long conversation){return db.queryForList("SELECT * FROM booking_calls WHERE conversation_id=? AND status IN ('RINGING','ACCEPTED')",conversation);}
    public boolean busy(long caller,long callee){
        for(long user:new TreeSet<>(List.of(caller,callee)))db.queryForList("SELECT pg_advisory_xact_lock(?)",8_000_000_000L+user);
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_calls k JOIN conversations c ON c.id=k.conversation_id JOIN bookings b ON b.id=c.booking_id WHERE k.status IN ('RINGING','ACCEPTED') AND (k.status='ACCEPTED' OR k.created_at>now()-interval '60 seconds') AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED','IN_PROGRESS') AND (k.caller_id IN (?,?) OR k.callee_id IN (?,?)))",Boolean.class,caller,callee,caller,callee);
    }
    public long create(long conversation,long caller,long callee,String room,String key){return db.insert("INSERT INTO booking_calls(conversation_id,caller_id,callee_id,room_name,request_key) VALUES (?,?,?,?,?)",conversation,caller,callee,room,key);}
    public void event(long booking,String json){db.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'CALL_INCOMING',CAST(? AS jsonb))",booking,json);}
    public void change(long id,String status){db.update("UPDATE booking_calls SET status=?,accepted_at=CASE WHEN ?='ACCEPTED' THEN now() ELSE accepted_at END,ended_at=CASE WHEN ?<>'ACCEPTED' THEN now() ELSE ended_at END WHERE id=?",status,status,status,id);}
    public List<Map<String,Object>> incoming(long user){return db.queryForList("SELECT k.id,c.booking_id AS \"bookingId\",c.id AS \"conversationId\",k.caller_id AS \"callerId\",k.status FROM booking_calls k JOIN conversations c ON c.id=k.conversation_id JOIN bookings b ON b.id=c.booking_id WHERE k.callee_id=? AND k.status='RINGING' AND k.created_at>now()-interval '60 seconds' AND b.status IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED','IN_PROGRESS') AND EXISTS(SELECT 1 FROM booking_assignments a WHERE a.booking_id=b.id AND a.staff_id=c.staff_id AND a.status='ACCEPTED') ORDER BY k.id DESC LIMIT 1",user);}
    public List<Long> maintenance(){return db.queryForList("SELECT k.id FROM booking_calls k JOIN conversations c ON c.id=k.conversation_id JOIN bookings b ON b.id=c.booking_id WHERE (k.status='RINGING' AND k.created_at<now()-interval '60 seconds') OR (k.status IN ('RINGING','ACCEPTED') AND (k.created_at<now()-interval '2 hours' OR b.status NOT IN ('CONFIRMED','ACCEPTED','STAFF_ASSIGNED','IN_PROGRESS') OR NOT EXISTS(SELECT 1 FROM booking_assignments a WHERE a.booking_id=b.id AND a.staff_id=c.staff_id AND a.status='ACCEPTED'))) OR (k.status NOT IN ('RINGING','ACCEPTED') AND k.room_closed_at IS NULL) ORDER BY k.id LIMIT 50",Long.class);}
    public void closed(long id){db.update("UPDATE booking_calls SET room_closed_at=now() WHERE id=?",id);}
}
