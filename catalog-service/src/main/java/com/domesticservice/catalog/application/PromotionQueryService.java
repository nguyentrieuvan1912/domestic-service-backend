package com.domesticservice.catalog.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PromotionQueryService {
    private final JdbcTemplate db;
    public PromotionQueryService(JdbcTemplate db) { this.db=db; }
    public List<Map<String,Object>> promotions() {
        Map<Long,List<Long>> scopes=new HashMap<>();
        for(var scope:db.queryForList("SELECT promotion_id,service_id FROM promotion_services ORDER BY promotion_id,service_id"))
            scopes.computeIfAbsent(((Number)scope.get("promotion_id")).longValue(),key->new ArrayList<>()).add(((Number)scope.get("service_id")).longValue());
        return db.query("""
            SELECT * FROM promotions WHERE status='ACTIVE' AND end_at>CURRENT_TIMESTAMP
              AND (usage_limit IS NULL OR used_count<usage_limit) ORDER BY start_at,id
            """,(rs,row)-> {
                var item=new LinkedHashMap<String,Object>();
                item.put("id",rs.getLong("id")); item.put("code",rs.getString("promotion_code"));
                item.put("name",rs.getString("promotion_name")); item.put("description",Objects.toString(rs.getString("description"),""));
                item.put("discountType",rs.getString("discount_type")); item.put("discountValue",rs.getLong("discount_value"));
                item.put("maxDiscountAmount",rs.getObject("max_discount_amount")); item.put("minimumBookingAmount",rs.getLong("minimum_booking_amount"));
                item.put("startDate",rs.getTimestamp("start_at").toInstant().toString()); item.put("endDate",rs.getTimestamp("end_at").toInstant().toString());
                item.put("usageLimit",rs.getObject("usage_limit")); item.put("usedCount",rs.getInt("used_count"));
                item.put("isActive",!rs.getTimestamp("start_at").toInstant().isAfter(java.time.Instant.now()));
                item.put("serviceIds",scopes.getOrDefault(rs.getLong("id"),List.of()));
                return item;
            });
    }
}
