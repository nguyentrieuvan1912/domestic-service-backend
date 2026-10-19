package com.domesticservice.identity.repository;

import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class AdministrativeUnitsSqlRepository {

    private final NativeQueryExecutor db;

    public AdministrativeUnitsSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public List<Map<String, Object>> hcmFindAdministrativeUnits(Object... args) {
        return db.query("SELECT id,name,unit_type FROM administrative_units WHERE province='TP. Hồ Chí Minh' ORDER BY name,id", (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "name", rs.getString("name"), "type", rs.getString("unit_type")), args);
    }
}
