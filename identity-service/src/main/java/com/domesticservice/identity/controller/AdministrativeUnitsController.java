package com.domesticservice.identity.controller;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/administrative-units")
public class AdministrativeUnitsController {
    private final JdbcTemplate db;
    public AdministrativeUnitsController(JdbcTemplate db) { this.db=db; }
    @GetMapping("/hcm")
    public Map<String,Object> hcm() {
        var items=db.query("SELECT id,name,unit_type FROM administrative_units WHERE province='TP. Hồ Chí Minh' ORDER BY name,id",(rs,row)->Map.<String,Object>of("id",rs.getLong("id"),"name",rs.getString("name"),"type",rs.getString("unit_type")));
        return Map.of("province","TP. Hồ Chí Minh","effectiveFrom","2025-07-01","resolution","1685/NQ-UBTVQH15","items",items,"total",items.size());
    }
}
