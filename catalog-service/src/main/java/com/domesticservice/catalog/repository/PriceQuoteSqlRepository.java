package com.domesticservice.catalog.repository;

import java.util.*;
import com.domesticservice.catalog.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class PriceQuoteSqlRepository {

    private final NativeQueryExecutor db;

    public PriceQuoteSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> calculateFindPromotions(Object... args) {
        return db.queryForList("SELECT * FROM promotions WHERE promotion_code=? AND status='ACTIVE' AND start_at<=now() AND end_at>now() AND (usage_limit IS NULL OR used_count<usage_limit)", args);
    }

    public <T> List<T> calculateFindPromotionServices(Class<T> type, Object... args) {
        return db.queryForList("SELECT service_id FROM promotion_services WHERE promotion_id=?", type, args);
    }

    public void createLockRecords(long user, Object... args) {
        db.execute("SELECT pg_advisory_xact_lock(" + user + ")");
    }

    public List<Map<String, Object>> createFindPriceQuotes(Object... args) {
        return db.queryForList("SELECT * FROM price_quotes WHERE customer_id=? AND request_key=?", args);
    }

    public Map<String, Object> createFindPriceQuotes2(Object... args) {
        return db.queryForMap("INSERT INTO price_quotes(customer_id,service_id,package_id,snapshot,base_amount,add_on_amount,discount_amount,total_amount,expires_at,request_key,request_hash) VALUES (?,?,?,CAST(? AS jsonb),?,?,?,?,now()+interval '10 minutes',?,?) RETURNING *", args);
    }

    public List<Map<String, Object>> consumeFindPriceQuotesForUpdate(Object... args) {
        return db.queryForList("SELECT * FROM price_quotes WHERE id=? FOR UPDATE", args);
    }

    public int consumeUpdatePromotions(Object... args) {
        return db.update("UPDATE promotions SET used_count=used_count+1 WHERE id=? AND status='ACTIVE' AND start_at<=now() AND end_at>now() AND (usage_limit IS NULL OR used_count<usage_limit)", args);
    }

    public int consumeUpdatePriceQuotes(Object... args) {
        return db.update("UPDATE price_quotes SET consumed_booking_id=? WHERE id=?", args);
    }
}
