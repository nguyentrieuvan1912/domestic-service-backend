package com.domesticservice.booking.repository;

import java.util.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class PublicStaffStatisticsSqlRepository {

    private final NativeQueryExecutor db;

    public PublicStaffStatisticsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> statsCountBookingAssignments(Object... args) {
        return db.queryForList("""
            WITH a AS (SELECT staff_id,count(*) FILTER(WHERE status='COMPLETED') AS completed FROM booking_assignments GROUP BY staff_id),
                 r AS (SELECT staff_id,count(*) AS reviews,avg(rating) AS rating FROM reviews WHERE moderation_status IN ('PUBLISHED','RESOLVED') GROUP BY staff_id),
                 p AS (SELECT DISTINCT staff_id FROM staff_service_capabilities WHERE status='APPROVED' UNION SELECT staff_id FROM a UNION SELECT staff_id FROM r)
            SELECT p.staff_id AS "staffId",coalesce(a.completed,0) AS "completedBookingsCount",coalesce(r.reviews,0) AS "reviewCount",coalesce(r.rating,5) AS rating
            FROM p LEFT JOIN a ON a.staff_id=p.staff_id LEFT JOIN r ON r.staff_id=p.staff_id
            """, args);
    }

    public <T> List<T> statsFindStaffServiceCapabilities(Class<T> type, Object... args) {
        return db.queryForList("SELECT service_id FROM staff_service_capabilities WHERE staff_id=? AND status='APPROVED' AND (valid_from IS NULL OR valid_from<=now()) AND (valid_until IS NULL OR valid_until>now())", type, args);
    }

    public List<Map<String, Object>> reviewsFindReviews(Object... args) {
        return db.queryForList("SELECT id,rating,comment,created_at AS \"createdAt\" FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED') ORDER BY id DESC LIMIT 100", args);
    }
}
