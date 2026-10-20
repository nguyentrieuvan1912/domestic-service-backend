package com.domesticservice.catalog.repository;

import com.domesticservice.catalog.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class PromotionQuerySqlRepository {

    private final NativeQueryExecutor db;

    public PromotionQuerySqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> promotionsFindPromotionServices(Object... args) {
        return db.queryForList("SELECT promotion_id,service_id FROM promotion_services ORDER BY promotion_id,service_id", args);
    }

    public List<Map<String, Object>> promotionsFindPromotions(Map<Long, List<Long>> scopes, Object... args) {
        return db.query("""
            SELECT * FROM promotions WHERE status='ACTIVE' AND end_at>CURRENT_TIMESTAMP
              AND (usage_limit IS NULL OR used_count<usage_limit) ORDER BY start_at,id
            """, (rs, row) -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("id", rs.getLong("id"));
            item.put("code", rs.getString("promotion_code"));
            item.put("name", rs.getString("promotion_name"));
            item.put("description", Objects.toString(rs.getString("description"), ""));
            item.put("discountType", rs.getString("discount_type"));
            item.put("discountValue", rs.getLong("discount_value"));
            item.put("maxDiscountAmount", rs.getObject("max_discount_amount"));
            item.put("minimumBookingAmount", rs.getLong("minimum_booking_amount"));
            item.put("startDate", rs.getTimestamp("start_at").toInstant().toString());
            item.put("endDate", rs.getTimestamp("end_at").toInstant().toString());
            item.put("usageLimit", rs.getObject("usage_limit"));
            item.put("usedCount", rs.getInt("used_count"));
            item.put("isActive", !rs.getTimestamp("start_at").toInstant().isAfter(java.time.Instant.now()));
            item.put("serviceIds", scopes.getOrDefault(rs.getLong("id"), List.of()));
            return item;
        }, args);
    }
}
