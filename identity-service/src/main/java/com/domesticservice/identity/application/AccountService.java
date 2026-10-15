package com.domesticservice.identity.application;

import com.domesticservice.identity.dto.AccountRequests.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AccountService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RegistrationVerificationService verification;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode("invalid-login-placeholder");
    private static final List<String> STAGES = List.of("PENDING", "ONBOARDING", "TRAINING", "EVALUATION");
    private static final List<String> DOCUMENTS = List.of("Bản sao CCCD", "Hộ khẩu / KT3", "Giấy khám sức khỏe");

    public AccountService(JdbcTemplate db, ObjectMapper json, RegistrationVerificationService verification) { this.db = db; this.json = json; this.verification=verification; }
    public record User(long id, String fullName, String phone, String email, String role, String status) {}
    public record Session(String accessToken, Instant expiresAt, User user) {}
    public RegistrationVerificationService.Challenge sendRegistrationCode(EmailStart request) { return verification.send(request.email()); }
    @Transactional(noRollbackFor=ResponseStatusException.class)
    public RegistrationVerificationService.Verified verifyRegistrationCode(EmailVerify request) { return verification.verify(request.challengeId(),request.code()); }

    public static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
    private String hashPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || password.length() < 8)
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự và không quá 72 byte.");
        return passwords.encode(password);
    }
    private long createUser(String name, String phone, String email, String password, String role) {
        try {
            return insert("""
                INSERT INTO users(user_name,password_hash,full_name,phone,email,role,status)
                VALUES (?,?,?,?,?,?,'ACTIVE')
                """, "account_"+UUID.randomUUID(), hashPassword(password), name.strip(),
                normalizePhone(phone.strip()), email.toLowerCase(Locale.ROOT).strip(), role);
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Email hoặc số điện thoại đã được sử dụng.");
        }
    }
    private String normalizePhone(String value) { return value.startsWith("+84") ? "0"+value.substring(3) : value; }
    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public Session register(Register request) {
        String email=verification.consume(request.verificationToken());
        long id = createUser(request.fullName(), request.phone(), email, request.password(), "CUSTOMER");
        db.update("INSERT INTO customers(id) VALUES (?)", id);
        return issue(id);
    }
    public void bootstrap(String email, String phone, String password) {
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE role='ADMIN' AND status='ACTIVE')", Boolean.class))) return;
        long id = createUser("Quản trị viên", phone, email, password, "ADMIN");
        db.update("INSERT INTO admins(id) VALUES (?)", id);
    }
    public Session login(Login request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw error(HttpStatus.UNAUTHORIZED,"Thông tin đăng nhập không đúng hoặc tài khoản không hoạt động.");
        var rows = db.queryForList("SELECT id,password_hash,status FROM users WHERE lower(email)=? OR phone=? OR lower(user_name)=?",
                request.identifier().strip().toLowerCase(Locale.ROOT), normalizePhone(request.identifier().strip()), request.identifier().strip().toLowerCase(Locale.ROOT));
        var row = rows.size() == 1 ? rows.getFirst() : null;
        String encoded = row == null ? dummyHash : (String) row.get("password_hash");
        boolean valid = passwords.matches(request.password(), encoded.startsWith("$2") ? encoded : dummyHash);
        if (row == null || !encoded.startsWith("$2") || !valid || !"ACTIVE".equals(row.get("status")))
            throw error(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không đúng hoặc tài khoản không hoạt động.");
        long id = ((Number) row.get("id")).longValue();
        db.update("UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE id=?", id);
        return issue(id);
    }
    private Session issue(long id) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = Instant.now().plus(8, ChronoUnit.HOURS);
        db.update("DELETE FROM account_sessions WHERE expires_at<CURRENT_TIMESTAMP");
        db.update("INSERT INTO account_sessions(user_id,token_hash,expires_at) VALUES (?,?,?)", id, digest(token), Timestamp.from(expiry));
        return new Session(token, expiry, user(id));
    }
    private String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public User authenticate(String header, boolean admin) {
        if (header == null || !header.startsWith("Bearer ") || header.length() > 200)
            throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        var ids = db.queryForList("""
            SELECT u.id FROM account_sessions s JOIN users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
            """, Long.class, digest(header.substring(7)));
        if (ids.isEmpty()) throw error(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập hết hạn hoặc tài khoản đã bị khóa.");
        User user = user(ids.getFirst());
        if (admin && !"ADMIN".equals(user.role())) throw error(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được phép thực hiện.");
        return user;
    }
    public void logout(String header) {
        if (header != null && header.startsWith("Bearer ")) db.update("DELETE FROM account_sessions WHERE token_hash=?", digest(header.substring(7)));
    }
    private User user(long id) {
        return db.queryForObject("SELECT id,full_name,phone,email,role,status FROM users WHERE id=?",
            (rs,n) -> new User(rs.getLong("id"),rs.getString("full_name"),rs.getString("phone"),rs.getString("email"),rs.getString("role"),rs.getString("status")), id);
    }
    public Map<String,Object> profile(User user) {
        return db.queryForObject("""
            SELECT u.avatar_url,u.created_at,c.gender customer_gender,c.date_of_birth customer_dob,
                   c.total_bookings,c.reward_points,s.gender staff_gender,s.date_of_birth staff_dob,s.birth_year,
                   s.identity_number,s.profile_description,s.experience_years,s.average_rating,s.approved_at,
                   s.total_reviews,s.completion_rate,s.satisfaction_rate,s.status staff_status,s.is_online,
                   (SELECT count(*) FROM addresses a WHERE a.customer_id=u.id) address_count,
                   (SELECT concat_ws(', ',a.detail_address,a.ward,a.district,a.province) FROM addresses a WHERE a.customer_id=u.id AND a.is_default=true) default_address
            FROM users u LEFT JOIN customers c ON c.id=u.id LEFT JOIN staffs s ON s.id=u.id WHERE u.id=?
            """,(rs,n)-> {
                Map<String,Object> result=new LinkedHashMap<>();
                result.put("id",user.id()); result.put("fullName",user.fullName()); result.put("phone",user.phone());
                result.put("email",user.email()); result.put("role",user.role()); result.put("status",user.status());
                result.put("avatar",Objects.toString(rs.getString("avatar_url"),"")); result.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());
                result.put("gender",rs.getString(user.role().equals("CUSTOMER")?"customer_gender":"staff_gender"));
                var dob=rs.getDate(user.role().equals("CUSTOMER")?"customer_dob":"staff_dob"); result.put("dateOfBirth",dob==null?null:dob.toString());result.put("birthYear",rs.getObject("birth_year"));
                result.put("totalBookings",rs.getInt("total_bookings")); result.put("rewardPoints",rs.getLong("reward_points"));
                result.put("isVerified",rs.getTimestamp("approved_at")!=null); result.put("identityCard",rs.getString("identity_number")); result.put("bio",rs.getString("profile_description"));
                result.put("experienceYears",rs.getBigDecimal("experience_years")); result.put("rating",rs.getInt("total_reviews")==0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));
                result.put("reviewCount",rs.getInt("total_reviews")); result.put("completionRate",rs.getBigDecimal("completion_rate"));
                result.put("satisfactionRate",rs.getBigDecimal("satisfaction_rate")); result.put("staffStatus",rs.getString("staff_status")); result.put("isOnline",rs.getBoolean("is_online"));
                result.put("addressCount",rs.getInt("address_count")); result.put("defaultAddress",rs.getString("default_address"));
                return result;
            },user.id());
    }
    public Map<String,Object> updateCustomerProfile(User current,CustomerProfile request) {
        if (!"CUSTOMER".equals(current.role())) throw error(HttpStatus.FORBIDDEN,"Chỉ khách hàng được chỉnh sửa hồ sơ này.");
        try { db.update("UPDATE users SET full_name=?,phone=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
            request.fullName().strip(),normalizePhone(request.phone()),current.id()); }
        catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"Số điện thoại đã được sử dụng."); }
        return profile(user(current.id()));
    }
    public Map<String,Object> updateStaffProfile(User current,StaffProfile request) {
        if (!"STAFF".equals(current.role())) throw error(HttpStatus.FORBIDDEN,"Chỉ nhân viên được chỉnh sửa hồ sơ này.");
        try { db.update("UPDATE users SET phone=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",normalizePhone(request.phone()),current.id()); }
        catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"Số điện thoại đã được sử dụng."); }
        db.update("UPDATE staffs SET profile_description=? WHERE id=?",Objects.toString(request.bio(),"").strip(),current.id());
        return profile(user(current.id()));
    }
    public List<Map<String,Object>> publicStaff() {
        return db.query("SELECT u.id,u.full_name,u.avatar_url,u.created_at,s.gender,s.date_of_birth,s.birth_year,s.experience_years,s.average_rating,s.total_reviews,s.profile_description,s.status,s.is_online,s.approved_at FROM users u JOIN staffs s ON s.id=u.id WHERE u.status='ACTIVE' AND s.approved_at IS NOT NULL ORDER BY u.id",(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("id",rs.getLong("id"));item.put("fullName",rs.getString("full_name"));item.put("avatar",rs.getString("avatar_url"));item.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());item.put("gender",rs.getString("gender"));
            var date=rs.getDate("date_of_birth");item.put("dateOfBirth",date==null?null:date.toLocalDate().toString());item.put("birthYear",rs.getObject("birth_year"));item.put("experienceYears",rs.getBigDecimal("experience_years"));item.put("rating",rs.getInt("total_reviews")==0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));item.put("reviewCount",rs.getInt("total_reviews"));item.put("bio",rs.getString("profile_description"));item.put("status",rs.getString("status"));item.put("isOnline",rs.getBoolean("is_online"));item.put("isVerified",rs.getTimestamp("approved_at")!=null);return item;
        });
    }
    public Map<String,Object> createStaff(User admin,StaffCreate body) {
        if(!"ADMIN".equals(admin.role()))throw error(HttpStatus.FORBIDDEN,"Chỉ Admin được cấp tài khoản nhân viên.");
        long id=createUser(body.fullName(),body.phone(),body.email(),body.password(),"STAFF");
        try{db.update("INSERT INTO staffs(id,identity_number,account_rank,approved_at,approved_by) VALUES (?,?,?,now(),?)",id,body.identityCard().strip(),body.rank(),admin.id());}
        catch(DataIntegrityViolationException ex){throw error(HttpStatus.CONFLICT,"Thông tin nhân viên đã được sử dụng.");}
        saveStaffDetails(id,body.dateOfBirth(),body.birthYear(),body.gender(),body.experienceYears(),body.bio());
        return profile(user(id));
    }
    public Map<String,Object> staffOnline(User user,boolean online) {
        if(!"STAFF".equals(user.role()))throw error(HttpStatus.FORBIDDEN,"Chỉ nhân viên được cập nhật trạng thái này.");
        String state=db.queryForObject("SELECT status FROM staffs WHERE id=? FOR UPDATE",String.class,user.id());
        if(online && List.of("SUSPENDED","RESTRICTED").contains(state))throw error(HttpStatus.CONFLICT,"Hồ sơ đang bị hạn chế nhận việc.");
        db.update("UPDATE staffs SET is_online=?,status=CASE WHEN status IN ('SUSPENDED','RESTRICTED') THEN status ELSE ? END WHERE id=?",online,online?"AVAILABLE":"OFFLINE",user.id());
        return profile(user);
    }
    public List<Map<String,Object>> customers() {
        return db.query("""
            SELECT u.*,c.total_bookings, (SELECT concat_ws(', ',a.detail_address,a.ward,a.district,a.province)
                FROM addresses a WHERE a.customer_id=u.id AND a.is_default=true) address
            FROM users u JOIN customers c ON c.id=u.id ORDER BY u.id DESC
            """, (rs,n) -> {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("id",rs.getLong("id")); item.put("fullName",rs.getString("full_name"));
                item.put("phone",rs.getString("phone")); item.put("email",Objects.toString(rs.getString("email"),""));
                item.put("status",rs.getString("status")); item.put("statusNote",rs.getString("status_note"));
                item.put("address",Objects.toString(rs.getString("address"),"Chưa có địa chỉ mặc định"));
                item.put("joinDate",rs.getTimestamp("created_at").toInstant().toString());
                item.put("totalBookings",rs.getInt("total_bookings")); item.put("totalSpent",null);
                item.put("recentBookings",List.of()); return item;
            });
    }
    public void status(long id, String role, Status request) {
        if ("LOCKED".equals(request.status()) && (request.statusNote()==null || request.statusNote().isBlank()))
            throw error(HttpStatus.BAD_REQUEST,"Vui lòng nhập lý do khóa tài khoản.");
        int changed = db.update("UPDATE users SET status=?,status_note=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND role=?",
            "LOCKED".equals(request.status()) ? "BLOCKED" : request.status(), "ACTIVE".equals(request.status()) ? "" : Objects.toString(request.statusNote(),""),id,role);
        if (changed == 0) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản.");
        if (!"ACTIVE".equals(request.status())) db.update("DELETE FROM account_sessions WHERE user_id=?", id);
    }
    public List<Map<String,Object>> candidates() {
        return db.query("SELECT * FROM staff_candidates ORDER BY id DESC", (rs,n) -> candidate(rs.getLong("id"),rs.getString("payload"),rs.getObject("staff_id"),rs.getTimestamp("created_at")));
    }
    private Map<String,Object> candidate(long id, String payload, Object staffId, Timestamp created) {
        Map<String,Object> result = json.readValue(payload,new TypeReference<Map<String,Object>>(){});
        result.put("id",id); result.put("staffId",staffId); result.put("registeredAt",created.toInstant().toString()); return result;
    }
    private String text(Map<String,Object> data, String key, int max, boolean required) {
        Object value=data.get(key);
        if (value != null && !(value instanceof String)) throw error(HttpStatus.BAD_REQUEST,"Thông tin " + key + " không hợp lệ.");
        String result=Objects.toString(value,"").strip();
        if (result.length()>max || (required && result.isBlank())) throw error(HttpStatus.BAD_REQUEST,"Vui lòng kiểm tra " + key + ".");
        return result;
    }
    private List<String> strings(Map<String,Object> data, String key) {
        Object value=data.get(key);
        if (!(value instanceof List<?> values) || values.size()>50 || values.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank() || s.length()>200))
            throw error(HttpStatus.BAD_REQUEST,"Danh sách " + key + " không hợp lệ.");
        return values.stream().map(String.class::cast).distinct().toList();
    }
    public Map<String,Object> newCandidate(Map<String,Object> input) {
        var data = new LinkedHashMap<String,Object>();
        for (String key : List.of("fullName","phone","email","cccd","gender","area","source","note"))
            data.put(key,text(input,key,key.equals("note")?2000:key.equals("fullName")?160:254,!key.equals("note")));
        if (!text(input,"phone",30,true).matches("(?:0[0-9]{9}|\\+84[0-9]{9})") || !text(input,"cccd",30,true).matches("[0-9]{12}")
                || !text(input,"email",254,true).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw error(HttpStatus.BAD_REQUEST,"Số điện thoại, email hoặc CCCD không hợp lệ.");
        if (!List.of("Nam","Nữ","Khác").contains(data.get("gender")) || !List.of("Website","Google Forms","Zalo","Walk-in").contains(data.get("source")))
            throw error(HttpStatus.BAD_REQUEST,"Giới tính hoặc nguồn ứng viên không hợp lệ.");
        var fullBirth=staffBirthDate(Objects.toString(input.get("dateOfBirth"),""));
        Object year=fullBirth==null?input.get("birthYear"):fullBirth.getYear();
        if(fullBirth!=null)data.put("dateOfBirth",fullBirth.toString());
        if (!(year instanceof Number number) || number.doubleValue()!=number.intValue() || number.intValue()<1940 || number.intValue()>java.time.Year.now().getValue()-18)
            throw error(HttpStatus.BAD_REQUEST,"Ứng viên phải từ 18 tuổi; kiểm tra năm sinh.");
        data.put("birthYear",year);
        Object experience=input.get("experienceYears");if(experience instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0&&n.doubleValue()<=80)data.put("experienceYears",n);
        var services=strings(input,"services");
        if (services.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"Vui lòng chọn dịch vụ mong muốn.");
        data.put("services",services); data.put("status","PENDING"); data.put("subStatus","Chờ duyệt");
        long id=insert("INSERT INTO staff_candidates(payload) VALUES (?)",json.writeValueAsString(data));
        return findCandidate(id,false);
    }
    private Map<String,Object> findCandidate(long id, boolean lock) {
        var rows = db.query("SELECT * FROM staff_candidates WHERE id=?"+(lock?" FOR UPDATE":""),
            (rs,n)->candidate(rs.getLong("id"),rs.getString("payload"),rs.getObject("staff_id"),rs.getTimestamp("created_at")),id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy ứng viên.");
        return rows.getFirst();
    }
    public Map<String,Object> advance(long id, Map<String,Object> input) {
        var data=findCandidate(id,true);
        if (data.get("staffId")!=null) throw error(HttpStatus.CONFLICT,"Ứng viên đã được cấp tài khoản.");
        String old=(String)data.get("status"), next=text(input,"status",20,true);
        if (STAGES.indexOf(next)!=STAGES.indexOf(old)+1) throw error(HttpStatus.CONFLICT,"Phải hoàn tất từng bước xét duyệt theo thứ tự.");
        if (next.equals("TRAINING")) {
            var documents=strings(input,"documentChecklist");
            if (!documents.containsAll(DOCUMENTS)) throw error(HttpStatus.BAD_REQUEST,"Cần xác nhận đủ ba giấy tờ trước khi đào tạo.");
            data.put("documentChecklist",documents); data.put("onboardingNote",text(input,"onboardingNote",2000,true));
        }
        if (next.equals("EVALUATION")) {
            Object score=input.get("trainingScore");
            if (!(score instanceof Number number) || number.doubleValue()<0 || number.doubleValue()>100 || !Double.isFinite(number.doubleValue()))
                throw error(HttpStatus.BAD_REQUEST,"Điểm đào tạo phải từ 0 đến 100.");
            data.put("trainingScore",score); data.put("trainingNotes",text(input,"trainingNotes",2000,true));
        }
        data.put("status",next); data.put("subStatus",switch(next){case "ONBOARDING"->"Đang tiếp nhận hồ sơ";case "TRAINING"->"Đang học việc";default->"Chờ xếp hạng";});
        saveCandidate(id,data); return data;
    }
    private void saveCandidate(long id, Map<String,Object> data) {
        db.update("UPDATE staff_candidates SET payload=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",json.writeValueAsString(data),data.get("status"),id);
    }
    public Map<String,Object> provision(long id, Provision request, long adminId) {
        var data=findCandidate(id,true);
        if (data.get("staffId")!=null) throw error(HttpStatus.CONFLICT,"Ứng viên đã được cấp tài khoản.");
        if (!"EVALUATION".equals(data.get("status")) || data.get("trainingScore")==null)
            throw error(HttpStatus.CONFLICT,"Chỉ cấp tài khoản sau khi hoàn tất xét duyệt và đào tạo.");
        long staffId=createUser((String)data.get("fullName"),(String)data.get("phone"),(String)data.get("email"),request.password(),"STAFF");
        try {
            db.update("""
                INSERT INTO staffs(id,identity_number,gender,profile_description,approved_at,approved_by,account_rank)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP,?,?)
                """,staffId,data.get("cccd"),switch((String)data.get("gender")){case "Nam"->"MALE";case "Nữ"->"FEMALE";default->"OTHER";},
                data.get("note"),adminId,request.rank());
        } catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"CCCD đã thuộc về nhân viên khác."); }
        saveStaffDetails(staffId,Objects.toString(data.get("dateOfBirth"),""),data.get("birthYear") instanceof Number n?n.intValue():null,null,data.get("experienceYears") instanceof Number n?n.doubleValue():null,null);
        data.put("staffId",staffId); data.put("rank",request.rank()); data.put("subStatus","Đã chuyển thành nhân viên chính thức");
        saveCandidate(id,data); db.update("UPDATE staff_candidates SET staff_id=? WHERE id=?",staffId,id); return data;
    }
    public List<Map<String,Object>> staff() {
        return db.query("SELECT u.*,s.identity_number,s.average_rating,s.total_reviews,s.account_rank,s.date_of_birth,s.birth_year,s.gender,s.experience_years,s.profile_description FROM users u JOIN staffs s ON s.id=u.id ORDER BY u.id DESC",(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",rs.getLong("id")); item.put("fullName",rs.getString("full_name")); item.put("phone",rs.getString("phone"));
            item.put("identityCard",rs.getString("identity_number")); item.put("status",rs.getString("status")); item.put("statusNote",rs.getString("status_note"));
            item.put("rank",rs.getString("account_rank")); item.put("joinDate",rs.getTimestamp("created_at").toInstant().toString());
            item.put("averageRating",rs.getInt("total_reviews")==0 ? java.math.BigDecimal.valueOf(5) : rs.getBigDecimal("average_rating"));item.put("dateOfBirth",rs.getDate("date_of_birth")==null?null:rs.getDate("date_of_birth").toString());item.put("birthYear",rs.getObject("birth_year"));item.put("gender",rs.getString("gender"));item.put("experienceYears",rs.getBigDecimal("experience_years"));item.put("bio",rs.getString("profile_description"));item.put("avatar",rs.getString("avatar_url"));item.put("email",rs.getString("email"));return item;
        });
    }
    public void updateStaff(long id, StaffUpdate request) {
        status(id,"STAFF",new Status(request.status(),request.statusNote()));
        try { db.update("UPDATE users SET full_name=?,phone=? WHERE id=?",request.fullName().strip(),normalizePhone(request.phone()),id); }
        catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"Số điện thoại đã được sử dụng."); }
        db.update("UPDATE staffs SET account_rank=? WHERE id=?",request.rank(),id);
        saveStaffDetails(id,request.dateOfBirth(),request.birthYear(),request.gender(),request.experienceYears(),request.bio());
    }
    static java.time.LocalDate staffBirthDate(String raw){
        if(raw==null||raw.isBlank())return null;
        try{var date=java.time.LocalDate.parse(raw);var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));if(date.getYear()<1940||date.isAfter(today.minusYears(18)))throw error(HttpStatus.BAD_REQUEST,"Nhân viên phải đủ 18 tuổi; kiểm tra ngày sinh.");return date;}
        catch(java.time.DateTimeException ex){throw error(HttpStatus.BAD_REQUEST,"Ngày sinh không hợp lệ.");}
    }
    private void saveStaffDetails(long id,String raw,Integer year,String gender,Double experience,String bio){
        var dob=staffBirthDate(raw);if(dob!=null)year=dob.getYear();
        if(year!=null&&(year<1940||year>java.time.Year.now().getValue()-18))throw error(HttpStatus.BAD_REQUEST,"Năm sinh nhân viên không hợp lệ.");
        if(dob!=null)db.update("UPDATE staffs SET date_of_birth=?,birth_year=? WHERE id=?",dob,year,id);
        else if(year!=null)db.update("UPDATE staffs SET birth_year=? WHERE id=?",year,id);
        if(gender!=null)db.update("UPDATE staffs SET gender=? WHERE id=?",gender,id);
        if(experience!=null)db.update("UPDATE staffs SET experience_years=? WHERE id=?",experience,id);
        if(bio!=null)db.update("UPDATE staffs SET profile_description=? WHERE id=?",bio.strip(),id);
        db.update("UPDATE users SET avatar_url='/api/v1/identity/staff-avatar.png' WHERE id=? AND coalesce(btrim(avatar_url),'')=''",id);
    }

}
