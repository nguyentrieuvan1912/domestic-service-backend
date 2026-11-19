package com.domesticservice.catalog.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogArtworkRepository {
    public record Banner(long id, long serviceId, String title, String badge, String description,
                         String buttonLabel, String theme, String layout, String imageUrl, int sortOrder, boolean active) {}

    private final JdbcTemplate db;

    public CatalogArtworkRepository(JdbcTemplate db) {
        this.db = db;
    }

    public List<Banner> banners(boolean publishedOnly) {
        String filter = publishedOnly ? " WHERE b.active AND s.status='ACTIVE' AND c.status='ACTIVE'" : "";
        return db.query("""
            SELECT b.* FROM home_banners b
            JOIN services s ON s.id=b.service_id
            JOIN service_categories c ON c.id=s.category_id
            """ + filter + " ORDER BY b.sort_order, b.id", (row, i) -> new Banner(
            row.getLong("id"), row.getLong("service_id"), row.getString("title"), row.getString("badge"),
            row.getString("description"), row.getString("button_label"), row.getString("theme"), row.getString("layout"),
            row.getString("image_url"), row.getInt("sort_order"), row.getBoolean("active")));
    }

    public boolean serviceExists(long id) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM services WHERE id=?)", Boolean.class, id));
    }

    public long storeImage(String sha256, byte[] content) {
        return db.queryForObject("""
            INSERT INTO catalog_images(sha256, content) VALUES (?, ?)
            ON CONFLICT (sha256) DO UPDATE SET sha256 = EXCLUDED.sha256 RETURNING id
            """, Long.class, sha256, content);
    }

    public Optional<byte[]> image(long id) {
        return db.query("SELECT content FROM catalog_images WHERE id=?", (row, i) -> row.getBytes(1), id).stream().findFirst();
    }

    public boolean updateServiceImage(long id, String url) {
        return db.update("UPDATE services SET image_url=?, updated_at=now(), version=version+1 WHERE id=?", url, id) == 1;
    }

    public Optional<String> lockBannerLayout(long id) {
        return db.query("SELECT layout FROM home_banners WHERE id=? FOR UPDATE", (row, i) -> row.getString(1), id).stream().findFirst();
    }

    public long saveBanner(Banner banner) {
        Object[] values = {banner.serviceId(), banner.title(), banner.badge(), banner.description(),
            banner.buttonLabel(), banner.theme(), banner.layout(), banner.imageUrl(), banner.sortOrder(), banner.active()};
        if (banner.id() == 0) {
            return db.queryForObject("""
                INSERT INTO home_banners(service_id,title,badge,description,button_label,theme,layout,image_url,sort_order,active)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, values);
        }
        var update = Arrays.copyOf(values, values.length + 1);
        update[values.length] = banner.id();
        db.update("""
            UPDATE home_banners SET service_id=?, title=?, badge=?, description=?, button_label=?, theme=?,
                layout=?, image_url=COALESCE(?, image_url), sort_order=?, active=? WHERE id=?
            """, update);
        return banner.id();
    }
}
