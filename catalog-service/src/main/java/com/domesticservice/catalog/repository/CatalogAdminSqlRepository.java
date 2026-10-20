package com.domesticservice.catalog.repository;

import java.util.*;
import com.domesticservice.catalog.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogAdminSqlRepository {

    private final NativeQueryExecutor db;

    public CatalogAdminSqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> T existsExistsWHERE(String table, Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table + " WHERE id=?)", type, args);
    }

    public List<Map<String, Object>> snapshotFindServiceCategories(Object... args) {
        return db.queryForList("SELECT id,category_name AS name,description,status FROM service_categories ORDER BY id", args);
    }

    public List<Map<String, Object>> snapshotFindServices(Object... args) {
        return db.queryForList("SELECT s.id,s.service_name AS name,s.description,s.category_id AS \"categoryId\",c.category_name AS \"categoryName\",s.status,s.service_code AS code,s.service_type AS \"serviceType\",s.price_unit AS \"priceUnit\",s.base_price AS \"basePrice\",s.estimated_duration_minutes AS \"durationMinutes\",s.requires_qualification AS \"requiresQualification\" FROM services s JOIN service_categories c ON c.id=s.category_id ORDER BY s.id", args);
    }

    public List<Map<String, Object>> snapshotFindServiceRequirements(Object... args) {
        return db.queryForList("SELECT id,field_key AS \"fieldKey\",label AS \"fieldName\",lower(field_type) AS \"fieldType\",required AS \"isRequired\",options::text AS options FROM service_requirements WHERE service_id=? AND status='ACTIVE' ORDER BY display_order,id", args);
    }

    public List<Map<String, Object>> snapshotFindServicePackages(Object... args) {
        return db.queryForList("SELECT id,service_id AS \"serviceId\",package_name AS name,description,base_price AS price,duration_minutes AS \"durationMinutes\",status,default_staff_count AS \"defaultStaffCount\",max_area AS \"maxArea\" FROM service_packages ORDER BY id", args);
    }

    public List<Map<String, Object>> snapshotFindAddOns(Object... args) {
        return db.queryForList("SELECT id,service_id AS \"serviceId\",name,description,price,extra_duration_minutes AS \"durationMinutes\",status FROM add_ons ORDER BY id", args);
    }

    public long saveInsertServiceCategories(Object... args) {
        return insert("INSERT INTO service_categories(category_code,category_name,description,status) VALUES (?,?,?,?)", args);
    }

    public int saveUpdateServiceCategories(Object... args) {
        return db.update("UPDATE service_categories SET category_name=?,description=?,status=?,updated_at=now() WHERE id=?", args);
    }

    public long saveInsertServices(Object... args) {
        return insert("INSERT INTO services(category_id,service_code,service_name,description,service_type,price_unit,base_price,estimated_duration_minutes,status) VALUES (?,?,?,?,?,?,?,?,?)", args);
    }

    public int saveUpdateServices(Object... args) {
        return db.update("UPDATE services SET category_id=?,service_name=?,description=?,status=?,updated_at=now(),version=version+1 WHERE id=?", args);
    }

    public int saveUpdateServiceRequirements(Object... args) {
        return db.update("UPDATE service_requirements SET status='INACTIVE' WHERE service_id=?", args);
    }

    public int saveUpdateServiceRequirements2(Object... args) {
        return db.update("UPDATE service_requirements SET label=?,field_type=?,required=?,options=CAST(? AS jsonb),display_order=?,status='ACTIVE' WHERE id=? AND service_id=?", args);
    }

    public long saveInsertServiceRequirements(Object... args) {
        return insert("INSERT INTO service_requirements(service_id,field_key,label,field_type,required,options,display_order) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)", args);
    }

    public long saveInsertServicePackages(Object... args) {
        return insert("INSERT INTO service_packages(service_id,package_name,description,base_price,duration_minutes,default_staff_count,status) VALUES (?,?,?,?,?,?,?)", args);
    }

    public int saveUpdateServicePackages(Object... args) {
        return db.update("UPDATE service_packages SET service_id=?,package_name=?,description=?,base_price=?,duration_minutes=?,status=?,updated_at=now() WHERE id=?", args);
    }

    public long saveInsertAddOns(Object... args) {
        return insert("INSERT INTO add_ons(service_id,name,description,price,extra_duration_minutes,status) VALUES (?,?,?,?,?,?)", args);
    }

    public int saveUpdateAddOns(Object... args) {
        return db.update("UPDATE add_ons SET service_id=?,name=?,description=?,price=?,extra_duration_minutes=?,status=?,updated_at=now() WHERE id=?", args);
    }

    public List<Map<String, Object>> promotionsFindPromotions(Object... args) {
        return db.queryForList("SELECT id,promotion_code AS code,promotion_name AS name,description,CASE discount_type WHEN 'PERCENTAGE' THEN 'PERCENT' ELSE 'FIXED' END AS \"discountType\",discount_value AS \"discountValue\",max_discount_amount AS \"maxDiscount\",minimum_booking_amount AS \"minimumBookingAmount\",start_at AS \"startsAt\",end_at AS \"endsAt\",usage_limit AS \"usageLimit\",used_count AS \"usedCount\",CASE WHEN status='INACTIVE' THEN 'DISABLED' WHEN end_at<=now() THEN 'EXPIRED' WHEN start_at>now() THEN 'SCHEDULED' ELSE 'ACTIVE' END AS status FROM promotions ORDER BY id DESC", args);
    }

    public long savePromotionInsertPromotions(Object... args) {
        return insert("INSERT INTO promotions(promotion_code,promotion_name,description,start_at,end_at,discount_type,discount_value,max_discount_amount,minimum_booking_amount,usage_limit,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)", args);
    }

    public <T> T savePromotionFindPromotionsForUpdate(Class<T> type, Object... args) {
        return db.queryForObject("SELECT used_count FROM promotions WHERE id=? FOR UPDATE", type, args);
    }

    public int savePromotionUpdatePromotions(Object... args) {
        return db.update("UPDATE promotions SET promotion_code=?,description=?,start_at=?,end_at=?,discount_type=?,discount_value=?,max_discount_amount=?,usage_limit=?,status=? WHERE id=?", args);
    }
}
