package com.domesticservice.identity.repository;

import java.util.*;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerFavoritesSqlRepository {

    private final NativeQueryExecutor db;

    public CustomerFavoritesSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> List<T> listFindCustomerFavorites(Class<T> type, Object... args) {
        return db.queryForList("SELECT service_id FROM customer_favorites WHERE customer_id=? ORDER BY created_at DESC", type, args);
    }

    public int saveInsertCustomerFavorites(Object... args) {
        return db.update("INSERT INTO customer_favorites(customer_id,service_id) VALUES (?,?) ON CONFLICT DO NOTHING", args);
    }

    public int saveDeleteCustomerFavorites(Object... args) {
        return db.update("DELETE FROM customer_favorites WHERE customer_id=? AND service_id=?", args);
    }
}
