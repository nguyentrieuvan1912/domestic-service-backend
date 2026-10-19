package com.domesticservice.identity.service;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.identity.repository.AccountSqlRepository;
import com.domesticservice.identity.dto.AccountResponse.*;
import com.domesticservice.identity.dto.RegistrationResponse.Challenge;
import com.domesticservice.identity.dto.RegistrationResponse.Verified;

@Service
@Transactional
public class AccountService {

    private final AccountSqlRepository repository;

    private final ObjectMapper json;

    private final RegistrationVerificationService verification;

    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    private final String dummyHash = passwords.encode("invalid-login-placeholder");

    private static final List<String> STAGES = List.of("PENDING", "ONBOARDING", "TRAINING", "EVALUATION");

    private static final List<String> DOCUMENTS = List.of("Bản sao CCCD", "Hộ khẩu / KT3", "Giấy khám sức khỏe");

    public AccountService(AccountSqlRepository repository, ObjectMapper json, RegistrationVerificationService verification) {
        this.repository = repository;
        this.json = json;
        this.verification = verification;
    }

    public Challenge sendRegistrationCode(EmailStart request) {
        return verification.send(request.email());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Verified verifyRegistrationCode(EmailVerify request) {
        return verification.verify(request.challengeId(), request.code());
    }

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
            return repository.createUserInsertUsers("account_" + UUID.randomUUID(), hashPassword(password), name.strip(), normalizePhone(phone.strip()), email.toLowerCase(Locale.ROOT).strip(), role);
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Email hoặc số điện thoại đã được sử dụng.");
        }
    }

    private String normalizePhone(String value) {
        return value.startsWith("+84") ? "0" + value.substring(3) : value;
    }

    public Session register(Register request) {
        String email = verification.consume(request.verificationToken());
        long id = createUser(request.fullName(), request.phone(), email, request.password(), "CUSTOMER");
        repository.registerInsertCustomers(id);
        return issue(id);
    }

    public void bootstrap(String email, String phone, String password) {
        if (Boolean.TRUE.equals(repository.bootstrapExistsUsers(Boolean.class)))
            return;
        long id = createUser("Quản trị viên", phone, email, password, "ADMIN");
        repository.bootstrapInsertAdmins(id);
    }

    public Session login(Login request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw error(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không đúng hoặc tài khoản không hoạt động.");
        var rows = repository.loginFindUsers(request.identifier().strip().toLowerCase(Locale.ROOT), normalizePhone(request.identifier().strip()), request.identifier().strip().toLowerCase(Locale.ROOT));
        var row = rows.size() == 1 ? rows.getFirst() : null;
        String encoded = row == null ? dummyHash : (String) row.get("password_hash");
        boolean valid = passwords.matches(request.password(), encoded.startsWith("$2") ? encoded : dummyHash);
        if (row == null || !encoded.startsWith("$2") || !valid || !"ACTIVE".equals(row.get("status")))
            throw error(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không đúng hoặc tài khoản không hoạt động.");
        long id = ((Number) row.get("id")).longValue();
        repository.loginUpdateUsers(id);
        return issue(id);
    }

    private Session issue(long id) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = Instant.now().plus(8, ChronoUnit.HOURS);
        repository.issueDeleteAccountSessions();
        repository.issueInsertAccountSessions(id, digest(token), Timestamp.from(expiry));
        return new Session(token, expiry, user(id));
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public User authenticate(String header, boolean admin) {
        if (header == null || !header.startsWith("Bearer ") || header.length() > 200)
            throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        var ids = repository.authenticateFindAccountSessions(Long.class, digest(header.substring(7)));
        if (ids.isEmpty())
            throw error(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập hết hạn hoặc tài khoản đã bị khóa.");
        User user = user(ids.getFirst());
        if (admin && !"ADMIN".equals(user.role()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được phép thực hiện.");
        return user;
    }

    public void logout(String header) {
        if (header != null && header.startsWith("Bearer "))
            repository.logoutDeleteAccountSessions(digest(header.substring(7)));
    }

    private User user(long id) {
        return repository.userFindUsers(id);
    }

    public Map<String, Object> profile(User user) {
        return repository.profileCountAddresses(user, user.id());
    }

    public Map<String, Object> updateCustomerProfile(User current, CustomerProfile request) {
        if (!"CUSTOMER".equals(current.role()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ khách hàng được chỉnh sửa hồ sơ này.");
        try {
            repository.updateCustomerProfileUpdateUsers(request.fullName().strip(), normalizePhone(request.phone()), current.id());
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }
        return profile(user(current.id()));
    }

    public Map<String, Object> updateStaffProfile(User current, StaffProfile request) {
        if (!"STAFF".equals(current.role()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ nhân viên được chỉnh sửa hồ sơ này.");
        try {
            repository.updateStaffProfileUpdateUsers(normalizePhone(request.phone()), current.id());
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }
        repository.updateStaffProfileUpdateStaffs(Objects.toString(request.bio(), "").strip(), current.id());
        return profile(user(current.id()));
    }

    public List<Map<String, Object>> publicStaff() {
        return repository.publicStaffFindUsers();
    }

    public Map<String, Object> createStaff(User admin, StaffCreate body) {
        if (!"ADMIN".equals(admin.role()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ Admin được cấp tài khoản nhân viên.");
        long id = createUser(body.fullName(), body.phone(), body.email(), body.password(), "STAFF");
        try {
            repository.createStaffInsertStaffs(id, body.identityCard().strip(), body.rank(), admin.id());
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Thông tin nhân viên đã được sử dụng.");
        }
        saveStaffDetails(id, body.dateOfBirth(), body.birthYear(), body.gender(), body.experienceYears(), body.bio());
        return profile(user(id));
    }

    public Map<String, Object> staffOnline(User user, boolean online) {
        if (!"STAFF".equals(user.role()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ nhân viên được cập nhật trạng thái này.");
        String state = repository.staffOnlineFindStaffsForUpdate(String.class, user.id());
        if (online && List.of("SUSPENDED", "RESTRICTED").contains(state))
            throw error(HttpStatus.CONFLICT, "Hồ sơ đang bị hạn chế nhận việc.");
        repository.staffOnlineUpdateStaffs(online, online ? "AVAILABLE" : "OFFLINE", user.id());
        return profile(user);
    }

    public List<Map<String, Object>> customers() {
        return repository.customersFindAddresses();
    }

    public void status(long id, String role, Status request) {
        if ("LOCKED".equals(request.status()) && (request.statusNote() == null || request.statusNote().isBlank()))
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do khóa tài khoản.");
        int changed = repository.statusUpdateUsers("LOCKED".equals(request.status()) ? "BLOCKED" : request.status(), "ACTIVE".equals(request.status()) ? "" : Objects.toString(request.statusNote(), ""), id, role);
        if (changed == 0)
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        if (!"ACTIVE".equals(request.status()))
            repository.statusDeleteAccountSessions(id);
    }

    public List<Map<String, Object>> candidates() {
        return repository.candidatesFindStaffCandidates();
    }

    private String text(Map<String, Object> data, String key, int max, boolean required) {
        Object value = data.get(key);
        if (value != null && !(value instanceof String))
            throw error(HttpStatus.BAD_REQUEST, "Thông tin " + key + " không hợp lệ.");
        String result = Objects.toString(value, "").strip();
        if (result.length() > max || (required && result.isBlank()))
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra " + key + ".");
        return result;
    }

    private List<String> strings(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof List<?> values) || values.size() > 50 || values.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank() || s.length() > 200))
            throw error(HttpStatus.BAD_REQUEST, "Danh sách " + key + " không hợp lệ.");
        return values.stream().map(String.class::cast).distinct().toList();
    }

    public Map<String, Object> newCandidate(Map<String, Object> input) {
        var data = new LinkedHashMap<String, Object>();
        for (String key : List.of("fullName", "phone", "email", "cccd", "gender", "area", "source", "note")) data.put(key, text(input, key, key.equals("note") ? 2000 : key.equals("fullName") ? 160 : 254, !key.equals("note")));
        if (!text(input, "phone", 30, true).matches("(?:0[0-9]{9}|\\+84[0-9]{9})") || !text(input, "cccd", 30, true).matches("[0-9]{12}") || !text(input, "email", 254, true).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw error(HttpStatus.BAD_REQUEST, "Số điện thoại, email hoặc CCCD không hợp lệ.");
        if (!List.of("Nam", "Nữ", "Khác").contains(data.get("gender")) || !List.of("Website", "Google Forms", "Zalo", "Walk-in").contains(data.get("source")))
            throw error(HttpStatus.BAD_REQUEST, "Giới tính hoặc nguồn ứng viên không hợp lệ.");
        var fullBirth = staffBirthDate(Objects.toString(input.get("dateOfBirth"), ""));
        Object year = fullBirth == null ? input.get("birthYear") : fullBirth.getYear();
        if (fullBirth != null)
            data.put("dateOfBirth", fullBirth.toString());
        if (!(year instanceof Number number) || number.doubleValue() != number.intValue() || number.intValue() < 1940 || number.intValue() > java.time.Year.now().getValue() - 18)
            throw error(HttpStatus.BAD_REQUEST, "Ứng viên phải từ 18 tuổi; kiểm tra năm sinh.");
        data.put("birthYear", year);
        Object experience = input.get("experienceYears");
        if (experience instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 80)
            data.put("experienceYears", n);
        var services = strings(input, "services");
        if (services.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng chọn dịch vụ mong muốn.");
        data.put("services", services);
        data.put("status", "PENDING");
        data.put("subStatus", "Chờ duyệt");
        long id = repository.newCandidateInsertStaffCandidates(json.writeValueAsString(data));
        return findCandidate(id, false);
    }

    private Map<String, Object> findCandidate(long id, boolean lock) {
        var rows = repository.findCandidateFindStaffCandidatesForUpdate(lock, id);
        if (rows.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy ứng viên.");
        return rows.getFirst();
    }

    public Map<String, Object> advance(long id, Map<String, Object> input) {
        var data = findCandidate(id, true);
        if (data.get("staffId") != null)
            throw error(HttpStatus.CONFLICT, "Ứng viên đã được cấp tài khoản.");
        String old = (String) data.get("status"), next = text(input, "status", 20, true);
        if (STAGES.indexOf(next) != STAGES.indexOf(old) + 1)
            throw error(HttpStatus.CONFLICT, "Phải hoàn tất từng bước xét duyệt theo thứ tự.");
        if (next.equals("TRAINING")) {
            var documents = strings(input, "documentChecklist");
            if (!documents.containsAll(DOCUMENTS))
                throw error(HttpStatus.BAD_REQUEST, "Cần xác nhận đủ ba giấy tờ trước khi đào tạo.");
            data.put("documentChecklist", documents);
            data.put("onboardingNote", text(input, "onboardingNote", 2000, true));
        }
        if (next.equals("EVALUATION")) {
            Object score = input.get("trainingScore");
            if (!(score instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > 100 || !Double.isFinite(number.doubleValue()))
                throw error(HttpStatus.BAD_REQUEST, "Điểm đào tạo phải từ 0 đến 100.");
            data.put("trainingScore", score);
            data.put("trainingNotes", text(input, "trainingNotes", 2000, true));
        }
        data.put("status", next);
        data.put("subStatus", switch(next) {
            case "ONBOARDING" ->
                "Đang tiếp nhận hồ sơ";
            case "TRAINING" ->
                "Đang học việc";
            default ->
                "Chờ xếp hạng";
        });
        saveCandidate(id, data);
        return data;
    }

    private void saveCandidate(long id, Map<String, Object> data) {
        repository.saveCandidateUpdateStaffCandidates(json.writeValueAsString(data), data.get("status"), id);
    }

    public Map<String, Object> provision(long id, Provision request, long adminId) {
        var data = findCandidate(id, true);
        if (data.get("staffId") != null)
            throw error(HttpStatus.CONFLICT, "Ứng viên đã được cấp tài khoản.");
        if (!"EVALUATION".equals(data.get("status")) || data.get("trainingScore") == null)
            throw error(HttpStatus.CONFLICT, "Chỉ cấp tài khoản sau khi hoàn tất xét duyệt và đào tạo.");
        long staffId = createUser((String) data.get("fullName"), (String) data.get("phone"), (String) data.get("email"), request.password(), "STAFF");
        try {
            repository.provisionInsertStaffs(staffId, data.get("cccd"), switch((String) data.get("gender")) {
                case "Nam" ->
                    "MALE";
                case "Nữ" ->
                    "FEMALE";
                default ->
                    "OTHER";
            }, data.get("note"), adminId, request.rank());
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "CCCD đã thuộc về nhân viên khác.");
        }
        saveStaffDetails(staffId, Objects.toString(data.get("dateOfBirth"), ""), data.get("birthYear") instanceof Number n ? n.intValue() : null, null, data.get("experienceYears") instanceof Number n ? n.doubleValue() : null, null);
        data.put("staffId", staffId);
        data.put("rank", request.rank());
        data.put("subStatus", "Đã chuyển thành nhân viên chính thức");
        saveCandidate(id, data);
        repository.provisionUpdateStaffCandidates(staffId, id);
        return data;
    }

    public List<Map<String, Object>> staff() {
        return repository.staffFindUsers();
    }

    public void updateStaff(long id, StaffUpdate request) {
        status(id, "STAFF", new Status(request.status(), request.statusNote()));
        try {
            repository.updateStaffUpdateUsers(request.fullName().strip(), normalizePhone(request.phone()), id);
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }
        repository.updateStaffUpdateStaffs(request.rank(), id);
        saveStaffDetails(id, request.dateOfBirth(), request.birthYear(), request.gender(), request.experienceYears(), request.bio());
    }

    static java.time.LocalDate staffBirthDate(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        try {
            var date = java.time.LocalDate.parse(raw);
            var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            if (date.getYear() < 1940 || date.isAfter(today.minusYears(18)))
                throw error(HttpStatus.BAD_REQUEST, "Nhân viên phải đủ 18 tuổi; kiểm tra ngày sinh.");
            return date;
        } catch (java.time.DateTimeException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày sinh không hợp lệ.");
        }
    }

    private void saveStaffDetails(long id, String raw, Integer year, String gender, Double experience, String bio) {
        var dob = staffBirthDate(raw);
        if (dob != null)
            year = dob.getYear();
        if (year != null && (year < 1940 || year > java.time.Year.now().getValue() - 18))
            throw error(HttpStatus.BAD_REQUEST, "Năm sinh nhân viên không hợp lệ.");
        if (dob != null)
            repository.saveStaffDetailsUpdateStaffs(dob, year, id);
        else if (year != null)
            repository.saveStaffDetailsUpdateStaffs2(year, id);
        if (gender != null)
            repository.saveStaffDetailsUpdateStaffs3(gender, id);
        if (experience != null)
            repository.saveStaffDetailsUpdateStaffs4(experience, id);
        if (bio != null)
            repository.saveStaffDetailsUpdateStaffs5(bio.strip(), id);
        repository.saveStaffDetailsUpdateUsers(id);
    }
}
