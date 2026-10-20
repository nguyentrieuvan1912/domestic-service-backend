package com.domesticservice.identity.repository;

import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;
import com.domesticservice.identity.dto.AccountResponse.User;
import com.domesticservice.identity.dto.CustomerRequests.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CustomerDataSqlRepository {

    private final NativeQueryExecutor db;

    public CustomerDataSqlRepository(NativeQueryExecutor db, tools.jackson.databind.ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> T lockCustomerFindCustomersForUpdate(Class<T> type, Object... args) {
        return db.queryForObject("SELECT id FROM customers WHERE id=? FOR UPDATE", type, args);
    }

    public List<Map<String, Object>> addressesFindAddresses(Object... args) {
        return db.query("SELECT * FROM addresses WHERE customer_id=? ORDER BY is_default DESC,id", this::address, args);
    }

    public List<Map<String, Object>> addressFindAddresses(Object... args) {
        return db.query("SELECT * FROM addresses WHERE id=? AND customer_id=?", this::address, args);
    }

    public <T> T saveAddressExistsAdministrativeUnits(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM administrative_units WHERE province=? AND name=?)", type, args);
    }

    public <T> T saveAddressExistsAddresses(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM addresses WHERE customer_id=? AND is_default=true)", type, args);
    }

    public int saveAddressUpdateAddresses(Object... args) {
        return db.update("UPDATE addresses SET is_default=false,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_default=true", args);
    }

    public long saveAddressInsertAddresses(Object... args) {
        return insert("INSERT INTO addresses(customer_id,title,receiver_name,receiver_phone,province,district,ward,detail_address,note,is_default) VALUES (?,?,?,?,?,?,?,?,?,?)", args);
    }

    public int saveAddressUpdateAddresses2(Object... args) {
        return db.update("UPDATE addresses SET title=?,receiver_name=?,receiver_phone=?,province=?,district=?,ward=?,detail_address=?,note=?,is_default=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=?", args);
    }

    public int setDefaultUpdateAddresses(Object... args) {
        return db.update("UPDATE addresses SET is_default=false,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_default=true", args);
    }

    public int setDefaultUpdateAddresses2(Object... args) {
        return db.update("UPDATE addresses SET is_default=true,updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=?", args);
    }

    public int deleteAddressDeleteAddresses(Object... args) {
        return db.update("DELETE FROM addresses WHERE id=? AND customer_id=?", args);
    }

    public <T> T deleteAddressExistsAddresses(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM addresses WHERE customer_id=? AND is_default=true)", type, args);
    }

    public <T> List<T> deleteAddressFindAddresses(Class<T> type, Object... args) {
        return db.queryForList("SELECT id FROM addresses WHERE customer_id=? ORDER BY id LIMIT 1", type, args);
    }

    public int deleteAddressUpdateAddresses(Object... args) {
        return db.update("UPDATE addresses SET is_default=true,updated_at=CURRENT_TIMESTAMP WHERE id=?", args);
    }

    public List<Map<String, Object>> notificationsFindNotifications(User user, Object... args) {
        return db.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC", (rs, row) -> notification(rs, row, user), args);
    }

    public List<Map<String, Object>> notificationFindNotifications(User user, Object... args) {
        return db.query("SELECT * FROM notifications WHERE id=? AND user_id=?", (rs, row) -> notification(rs, row, user), args);
    }

    public int markReadUpdateNotifications(Object... args) {
        return db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE user_id=? AND read_at IS NULL", args);
    }

    public int markReadUpdateNotifications2(Object... args) {
        return db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND read_at IS NULL", args);
    }

    public long contactInsertSupportRequests(Object... args) {
        return insert("INSERT INTO support_requests(sender_name,email,subject,message) VALUES (?,?,?,?)", args);
    }

    private Map<String, Object> address(ResultSet rs, int row) throws SQLException {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", rs.getLong("id"));
        value.put("customerId", rs.getLong("customer_id"));
        value.put("title", rs.getString("title"));
        value.put("recipientName", rs.getString("receiver_name"));
        value.put("recipientPhone", rs.getString("receiver_phone"));
        value.put("streetAddress", rs.getString("detail_address"));
        value.put("ward", rs.getString("ward"));
        value.put("district", rs.getString("district"));
        value.put("city", rs.getString("province"));
        value.put("fullAddress", String.join(", ", List.of(rs.getString("detail_address"), rs.getString("ward"), rs.getString("district"), rs.getString("province")).stream().filter(s -> !s.isBlank()).toList()));
        value.put("latitude", rs.getBigDecimal("latitude"));
        value.put("longitude", rs.getBigDecimal("longitude"));
        value.put("note", rs.getString("note"));
        value.put("isDefault", rs.getBoolean("is_default"));
        return value;
    }

    private Map<String, Object> notification(ResultSet rs, int row, User user) throws SQLException {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", rs.getLong("id"));
        value.put("userId", user.id());
        value.put("targetRole", user.role());
        value.put("type", rs.getString("notification_type"));
        value.put("title", rs.getString("title"));
        value.put("content", rs.getString("content"));
        value.put("referenceId", rs.getObject("reference_id"));
        value.put("data", json.readValue(rs.getString("payload"), new TypeReference<Map<String, Object>>() {
        }));
        value.put("isRead", rs.getTimestamp("read_at") != null);
        value.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        return value;
    }

    private final tools.jackson.databind.ObjectMapper json;
}
