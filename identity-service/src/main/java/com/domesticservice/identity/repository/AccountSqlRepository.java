package com.domesticservice.identity.repository;

import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;
import com.domesticservice.identity.dto.AccountRequests.*;
import java.sql.Timestamp;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.identity.dto.AccountResponse.*;

@Repository
public class AccountSqlRepository {

    private final NativeQueryExecutor db;

    public AccountSqlRepository(NativeQueryExecutor db, tools.jackson.databind.ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public long createUserInsertUsers(Object... args) {
        return insert("""
            INSERT INTO users(user_name,password_hash,full_name,phone,email,role,status)
            VALUES (?,?,?,?,?,?,'ACTIVE')
            """, args);
    }

    public int registerInsertCustomers(Object... args) {
        return db.update("INSERT INTO customers(id) VALUES (?)", args);
    }

    public <T> T bootstrapExistsUsers(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE role='ADMIN' AND status='ACTIVE')", type, args);
    }

    public int bootstrapInsertAdmins(Object... args) {
        return db.update("INSERT INTO admins(id) VALUES (?)", args);
    }

    public List<Map<String, Object>> loginFindUsers(Object... args) {
        return db.queryForList("SELECT id,password_hash,status FROM users WHERE lower(email)=? OR phone=? OR lower(user_name)=?", args);
    }

    public int loginUpdateUsers(Object... args) {
        return db.update("UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE id=?", args);
    }

    public int issueDeleteAccountSessions(Object... args) {
        return db.update("DELETE FROM account_sessions WHERE expires_at<CURRENT_TIMESTAMP", args);
    }

    public int issueInsertAccountSessions(Object... args) {
        return db.update("INSERT INTO account_sessions(user_id,token_hash,expires_at) VALUES (?,?,?)", args);
    }

    public <T> List<T> authenticateFindAccountSessions(Class<T> type, Object... args) {
        return db.queryForList("""
            SELECT u.id FROM account_sessions s JOIN users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
            """, type, args);
    }

    public int logoutDeleteAccountSessions(Object... args) {
        return db.update("DELETE FROM account_sessions WHERE token_hash=?", args);
    }

    public User userFindUsers(Object... args) {
        return db.queryForObject("SELECT id,full_name,phone,email,role,status FROM users WHERE id=?", (rs, n) -> new User(rs.getLong("id"), rs.getString("full_name"), rs.getString("phone"), rs.getString("email"), rs.getString("role"), rs.getString("status")), args);
    }

    public Map<String, Object> profileCountAddresses(User user, Object... args) {
        return db.queryForObject("""
            SELECT u.avatar_url,u.created_at,c.gender customer_gender,c.date_of_birth customer_dob,
                   c.total_bookings,c.reward_points,s.gender staff_gender,s.date_of_birth staff_dob,s.birth_year,
                   s.identity_number,s.profile_description,s.experience_years,s.average_rating,s.approved_at,
                   s.total_reviews,s.completion_rate,s.satisfaction_rate,s.status staff_status,s.is_online,
                   (SELECT count(*) FROM addresses a WHERE a.customer_id=u.id) address_count,
                   (SELECT concat_ws(', ',a.detail_address,a.ward,a.district,a.province) FROM addresses a WHERE a.customer_id=u.id AND a.is_default=true) default_address
            FROM users u LEFT JOIN customers c ON c.id=u.id LEFT JOIN staffs s ON s.id=u.id WHERE u.id=?
            """, (rs, n) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.id());
            result.put("fullName", user.fullName());
            result.put("phone", user.phone());
            result.put("email", user.email());
            result.put("role", user.role());
            result.put("status", user.status());
            result.put("avatar", Objects.toString(rs.getString("avatar_url"), ""));
            result.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            result.put("gender", rs.getString(user.role().equals("CUSTOMER") ? "customer_gender" : "staff_gender"));
            var dob = rs.getDate(user.role().equals("CUSTOMER") ? "customer_dob" : "staff_dob");
            result.put("dateOfBirth", dob == null ? null : dob.toString());
            result.put("birthYear", rs.getObject("birth_year"));
            result.put("totalBookings", rs.getInt("total_bookings"));
            result.put("rewardPoints", rs.getLong("reward_points"));
            result.put("isVerified", rs.getTimestamp("approved_at") != null);
            result.put("identityCard", rs.getString("identity_number"));
            result.put("bio", rs.getString("profile_description"));
            result.put("experienceYears", rs.getBigDecimal("experience_years"));
            result.put("rating", rs.getInt("total_reviews") == 0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));
            result.put("reviewCount", rs.getInt("total_reviews"));
            result.put("completionRate", rs.getBigDecimal("completion_rate"));
            result.put("satisfactionRate", rs.getBigDecimal("satisfaction_rate"));
            result.put("staffStatus", rs.getString("staff_status"));
            result.put("isOnline", rs.getBoolean("is_online"));
            result.put("addressCount", rs.getInt("address_count"));
            result.put("defaultAddress", rs.getString("default_address"));
            return result;
        }, args);
    }

    public int updateCustomerProfileUpdateUsers(Object... args) {
        return db.update("UPDATE users SET full_name=?,phone=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", args);
    }

    public int updateStaffProfileUpdateUsers(Object... args) {
        return db.update("UPDATE users SET phone=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", args);
    }

    public int updateStaffProfileUpdateStaffs(Object... args) {
        return db.update("UPDATE staffs SET profile_description=? WHERE id=?", args);
    }

    public List<Map<String, Object>> publicStaffFindUsers(Object... args) {
        return db.query("SELECT u.id,u.full_name,u.avatar_url,u.created_at,s.gender,s.date_of_birth,s.birth_year,s.experience_years,s.average_rating,s.total_reviews,s.profile_description,s.status,s.is_online,s.approved_at FROM users u JOIN staffs s ON s.id=u.id WHERE u.status='ACTIVE' AND s.approved_at IS NOT NULL ORDER BY u.id", (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("fullName", rs.getString("full_name"));
            item.put("avatar", rs.getString("avatar_url"));
            item.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            item.put("gender", rs.getString("gender"));
            var date = rs.getDate("date_of_birth");
            item.put("dateOfBirth", date == null ? null : date.toLocalDate().toString());
            item.put("birthYear", rs.getObject("birth_year"));
            item.put("experienceYears", rs.getBigDecimal("experience_years"));
            item.put("rating", rs.getInt("total_reviews") == 0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));
            item.put("reviewCount", rs.getInt("total_reviews"));
            item.put("bio", rs.getString("profile_description"));
            item.put("status", rs.getString("status"));
            item.put("isOnline", rs.getBoolean("is_online"));
            item.put("isVerified", rs.getTimestamp("approved_at") != null);
            return item;
        }, args);
    }

    public int createStaffInsertStaffs(Object... args) {
        return db.update("INSERT INTO staffs(id,identity_number,account_rank,approved_at,approved_by) VALUES (?,?,?,now(),?)", args);
    }

    public <T> T staffOnlineFindStaffsForUpdate(Class<T> type, Object... args) {
        return db.queryForObject("SELECT status FROM staffs WHERE id=? FOR UPDATE", type, args);
    }

    public int staffOnlineUpdateStaffs(Object... args) {
        return db.update("UPDATE staffs SET is_online=?,status=CASE WHEN status IN ('SUSPENDED','RESTRICTED') THEN status ELSE ? END WHERE id=?", args);
    }

    public List<Map<String, Object>> customersFindAddresses(Object... args) {
        return db.query("""
            SELECT u.*,c.total_bookings, (SELECT concat_ws(', ',a.detail_address,a.ward,a.district,a.province)
                FROM addresses a WHERE a.customer_id=u.id AND a.is_default=true) address
            FROM users u JOIN customers c ON c.id=u.id ORDER BY u.id DESC
            """, (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("fullName", rs.getString("full_name"));
            item.put("phone", rs.getString("phone"));
            item.put("email", Objects.toString(rs.getString("email"), ""));
            item.put("status", rs.getString("status"));
            item.put("statusNote", rs.getString("status_note"));
            item.put("address", Objects.toString(rs.getString("address"), "Chưa có địa chỉ mặc định"));
            item.put("joinDate", rs.getTimestamp("created_at").toInstant().toString());
            item.put("totalBookings", rs.getInt("total_bookings"));
            item.put("totalSpent", null);
            item.put("recentBookings", List.of());
            return item;
        }, args);
    }

    public int statusUpdateUsers(Object... args) {
        return db.update("UPDATE users SET status=?,status_note=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND role=?", args);
    }

    public int statusDeleteAccountSessions(Object... args) {
        return db.update("DELETE FROM account_sessions WHERE user_id=?", args);
    }

    public List<Map<String, Object>> candidatesFindStaffCandidates(Object... args) {
        return db.query("SELECT * FROM staff_candidates ORDER BY id DESC", (rs, n) -> candidate(rs.getLong("id"), rs.getString("payload"), rs.getObject("staff_id"), rs.getTimestamp("created_at")), args);
    }

    public long newCandidateInsertStaffCandidates(Object... args) {
        return insert("INSERT INTO staff_candidates(payload) VALUES (?)", args);
    }

    public List<Map<String, Object>> findCandidateFindStaffCandidatesForUpdate(boolean lock, Object... args) {
        return db.query("SELECT * FROM staff_candidates WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs, n) -> candidate(rs.getLong("id"), rs.getString("payload"), rs.getObject("staff_id"), rs.getTimestamp("created_at")), args);
    }

    public int saveCandidateUpdateStaffCandidates(Object... args) {
        return db.update("UPDATE staff_candidates SET payload=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", args);
    }

    public int provisionInsertStaffs(Object... args) {
        return db.update("""
            INSERT INTO staffs(id,identity_number,gender,profile_description,approved_at,approved_by,account_rank)
            VALUES (?,?,?,?,CURRENT_TIMESTAMP,?,?)
            """, args);
    }

    public int provisionUpdateStaffCandidates(Object... args) {
        return db.update("UPDATE staff_candidates SET staff_id=? WHERE id=?", args);
    }

    public List<Map<String, Object>> staffFindUsers(Object... args) {
        return db.query("SELECT u.*,s.identity_number,s.average_rating,s.total_reviews,s.account_rank,s.date_of_birth,s.birth_year,s.gender,s.experience_years,s.profile_description FROM users u JOIN staffs s ON s.id=u.id ORDER BY u.id DESC", (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("fullName", rs.getString("full_name"));
            item.put("phone", rs.getString("phone"));
            item.put("identityCard", rs.getString("identity_number"));
            item.put("status", rs.getString("status"));
            item.put("statusNote", rs.getString("status_note"));
            item.put("rank", rs.getString("account_rank"));
            item.put("joinDate", rs.getTimestamp("created_at").toInstant().toString());
            item.put("averageRating", rs.getInt("total_reviews") == 0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));
            item.put("dateOfBirth", rs.getDate("date_of_birth") == null ? null : rs.getDate("date_of_birth").toString());
            item.put("birthYear", rs.getObject("birth_year"));
            item.put("gender", rs.getString("gender"));
            item.put("experienceYears", rs.getBigDecimal("experience_years"));
            item.put("bio", rs.getString("profile_description"));
            item.put("avatar", rs.getString("avatar_url"));
            item.put("email", rs.getString("email"));
            return item;
        }, args);
    }

    public int updateStaffUpdateUsers(Object... args) {
        return db.update("UPDATE users SET full_name=?,phone=? WHERE id=?", args);
    }

    public int updateStaffUpdateStaffs(Object... args) {
        return db.update("UPDATE staffs SET account_rank=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateStaffs(Object... args) {
        return db.update("UPDATE staffs SET date_of_birth=?,birth_year=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateStaffs2(Object... args) {
        return db.update("UPDATE staffs SET birth_year=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateStaffs3(Object... args) {
        return db.update("UPDATE staffs SET gender=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateStaffs4(Object... args) {
        return db.update("UPDATE staffs SET experience_years=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateStaffs5(Object... args) {
        return db.update("UPDATE staffs SET profile_description=? WHERE id=?", args);
    }

    public int saveStaffDetailsUpdateUsers(Object... args) {
        return db.update("UPDATE users SET avatar_url='/api/v1/identity/staff-avatar.png' WHERE id=? AND coalesce(btrim(avatar_url),'')=''", args);
    }

    private Map<String, Object> candidate(long id, String payload, Object staffId, Timestamp created) {
        Map<String, Object> result = json.readValue(payload, new TypeReference<Map<String, Object>>() {
        });
        result.put("id", id);
        result.put("staffId", staffId);
        result.put("registeredAt", created.toInstant().toString());
        return result;
    }

    private final tools.jackson.databind.ObjectMapper json;
}
