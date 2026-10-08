package com.domesticservice.catalog.infrastructure.repository;

import com.domesticservice.catalog.dto.CatalogResponse.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CatalogReadRepository {
    private static final String VISIBLE = " FROM services s JOIN service_categories c ON c.id=s.category_id"
            + " WHERE s.status='ACTIVE' AND c.status='ACTIVE'";
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public CatalogReadRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Category> categories() {
        return jdbc.query("SELECT * FROM service_categories WHERE status='ACTIVE' ORDER BY category_name,id",
                (r, n) -> new Category(r.getLong("id"), r.getString("category_code"),
                        r.getString("category_name"), r.getString("description"),
                        r.getString("icon_url"), r.getString("category_group")));
    }

    public Page services(Long categoryId, String search, int page, int size) {
        String filter = VISIBLE;
        var params = new java.util.ArrayList<Object>();
        if (categoryId != null) {
            filter += " AND s.category_id=?";
            params.add(categoryId);
        }
        if (!search.isEmpty()) {
            // Treat %, _ and ! literally; all user input stays in bind parameters.
            filter += " AND (lower(s.service_name) LIKE ? ESCAPE '!' OR lower(s.service_code) LIKE ? ESCAPE '!'"
                    + " OR lower(coalesce(s.description,'')) LIKE ? ESCAPE '!')";
            String pattern = "%" + search.toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            params.add(pattern); params.add(pattern); params.add(pattern);
        }
        long total = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, params.toArray());
        params.add(size); params.add((long) page * size);
        List<ServiceSummary> items = jdbc.query("SELECT s.*,c.category_code,c.category_name" + filter
                + " ORDER BY s.service_name,s.id LIMIT ? OFFSET ?", this::summary, params.toArray());
        return new Page(items, total, page, size, (total + size - 1) / size);
    }

    public Optional<Detail> detail(long id) {
        var services = jdbc.query("SELECT s.*,c.category_code,c.category_name" + VISIBLE + " AND s.id=?",
                this::summary, id);
        if (services.isEmpty()) return Optional.empty();
        var content = jdbc.queryForMap("SELECT workflow,benefits FROM services WHERE id=?", id);
        var packages = jdbc.query("SELECT * FROM service_packages WHERE service_id=? AND status='ACTIVE' ORDER BY duration_minutes,id",
                (r, n) -> new ServicePackage(r.getLong("id"), r.getString("package_name"),
                        r.getString("description"), r.getInt("duration_minutes"), r.getLong("base_price"),
                        r.getInt("default_staff_count"), r.getBigDecimal("max_area")), id);
        var addOns = jdbc.query("SELECT * FROM add_ons WHERE service_id=? AND status='ACTIVE' ORDER BY id",
                (r, n) -> new AddOn(r.getLong("id"), r.getString("name"), r.getString("description"),
                        r.getLong("price"), r.getInt("extra_duration_minutes"), r.getString("image_url")), id);
        var requirements = jdbc.query("SELECT * FROM service_requirements WHERE service_id=? AND status='ACTIVE' ORDER BY display_order,id",
                (r, n) -> new Requirement(r.getLong("id"), r.getString("field_key"), r.getString("label"),
                        r.getString("field_type"), r.getBoolean("required"), json.readTree(r.getString("options")),
                        json.readTree(r.getString("validation_rules")), r.getInt("display_order")), id);
        return Optional.of(new Detail(services.getFirst(), packages, addOns, requirements,
                strings(content.get("workflow").toString()), strings(content.get("benefits").toString())));
    }

    private ServiceSummary summary(ResultSet r, int row) throws SQLException {
        return new ServiceSummary(r.getLong("id"), r.getLong("category_id"), r.getString("category_code"),
                r.getString("category_name"), r.getString("service_code"), r.getString("service_name"),
                r.getString("description"), r.getString("short_description"), r.getString("service_type"),
                r.getString("price_unit"), r.getLong("base_price"), r.getInt("estimated_duration_minutes"),
                r.getBoolean("requires_qualification"), r.getString("image_url"), strings(r.getString("highlights")));
    }

    private List<String> strings(String value) {
        return Arrays.asList(json.readValue(value, String[].class));
    }
}
