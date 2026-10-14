package com.domesticservice.booking.controller;
import com.domesticservice.booking.application.BookingOperationsService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/booking/public")
public class PublicStaffStatisticsController {
    private final JdbcTemplate db;private final BookingOperationsService ops;
    public PublicStaffStatisticsController(JdbcTemplate db,BookingOperationsService ops){this.db=db;this.ops=ops;}
    @GetMapping("/staff-stats") public List<Map<String,Object>> stats(){
        var items=db.queryForList("""
            WITH a AS (SELECT staff_id,count(*) FILTER(WHERE status='COMPLETED') AS completed FROM booking_assignments GROUP BY staff_id),
                 r AS (SELECT staff_id,count(*) AS reviews,avg(rating) AS rating FROM reviews WHERE moderation_status IN ('PUBLISHED','RESOLVED') GROUP BY staff_id),
                 p AS (SELECT DISTINCT staff_id FROM staff_service_capabilities WHERE status='APPROVED' UNION SELECT staff_id FROM a UNION SELECT staff_id FROM r)
            SELECT p.staff_id AS "staffId",coalesce(a.completed,0) AS "completedBookingsCount",coalesce(r.reviews,0) AS "reviewCount",coalesce(r.rating,0) AS rating
            FROM p LEFT JOIN a ON a.staff_id=p.staff_id LEFT JOIN r ON r.staff_id=p.staff_id
            """
        );
        // Catalog list is paginated, so use the complete service read model through the service helper.
        var names=ops.services(null);
        for(var item:items){var capabilities=db.queryForList("SELECT service_id FROM staff_service_capabilities WHERE staff_id=? AND status='APPROVED' AND (valid_from IS NULL OR valid_from<=now()) AND (valid_until IS NULL OR valid_until>now())",Long.class,item.get("staffId"));item.put("competencies",capabilities.stream().map(names::get).filter(Objects::nonNull).map(service->Objects.toString(service.get("name"),"")).toList());}
        return items;
    }
    @GetMapping("/staff/{id}/reviews") public List<Map<String,Object>> reviews(@PathVariable long id){return db.queryForList("SELECT id,rating,comment,created_at AS \"createdAt\" FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED') ORDER BY id DESC LIMIT 100",id);}
}
