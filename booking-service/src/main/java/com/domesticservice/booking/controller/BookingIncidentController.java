package com.domesticservice.booking.controller;
import com.domesticservice.booking.application.BookingOperationsService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/booking/admin/incidents")
public class BookingIncidentController {
    private final BookingOperationsService ops;private final JdbcTemplate db;
    public BookingIncidentController(BookingOperationsService ops,JdbcTemplate db){this.ops=ops;this.db=db;}
    @GetMapping public List<Map<String,Object>>list(@RequestHeader(value="Authorization",required=false)String token){ops.actor(token,"ADMIN");return db.queryForList("SELECT i.id,i.booking_id AS \"bookingId\",b.booking_code AS \"bookingCode\",i.reporter_role AS \"reporterRole\",i.reason,i.status,i.created_at AS \"createdAt\" FROM booking_incidents i JOIN bookings b ON b.id=i.booking_id ORDER BY i.id DESC LIMIT 500");}
    @PatchMapping("/{id}") @Transactional public Map<String,Object>save(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){ops.actor(token,"ADMIN");String status=Objects.toString(b.get("status"),"");if(!List.of("OPEN","RESOLVED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái sự cố không hợp lệ.");if(db.update("UPDATE booking_incidents SET status=? WHERE id=?",status,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo sự cố.");return Map.of("id",id,"status",status);}
}
