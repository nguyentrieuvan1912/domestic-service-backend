package com.domesticservice.identity.service;

import com.domesticservice.identity.dto.AccountRequests.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
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
import com.domesticservice.identity.repository.AccountSqlRepository.PasswordAccount;
import com.domesticservice.identity.dto.AccountResponse.*;
import com.domesticservice.identity.dto.RegistrationResponse.Challenge;
import com.domesticservice.identity.dto.RegistrationResponse.Verified;

@Service
@Transactional
public class AccountService {

    private final AccountSqlRepository repository;
    private final AvatarService avatars;

    private final ObjectMapper json;

    private final RegistrationVerificationService verification;
    private final RegistrationMailer mailer;

    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    private final String dummyHash = passwords.encode("invalid-login-placeholder");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration PASSWORD_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PASSWORD_FAILURES = 5;
    private static final String INVALID_LOGIN = "Thông tin đăng nhập không đúng hoặc tài khoản không hoạt động.";

    private static final List<String> STAGES = List.of("PENDING", "ONBOARDING", "TRAINING", "EVALUATION");

    private static final List<String> DOCUMENTS = List.of("Bản sao CCCD", "Hộ khẩu / KT3", "Giấy khám sức khỏe");

    public AccountService(AccountSqlRepository repository, ObjectMapper json, RegistrationVerificationService verification,
                          AvatarService avatars, RegistrationMailer mailer) {
        this.avatars = avatars;
        this.repository = repository;
        this.json = json;
        this.verification = verification;
        this.mailer = mailer;
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

    @Transactional(noRollbackFor = PasswordRejected.class)
    public Session login(Login request) {
        String identifier = request.identifier().strip().toLowerCase(Locale.ROOT);
        var rows = repository.loginFindUsers(identifier, normalizePhone(identifier));
        var row = rows.size() == 1 ? rows.getFirst() : null;
        Instant now = Instant.now();
        boolean active = row != null && "ACTIVE".equals(row.status());
        if (active) checkPasswordBudget(row, now);
        if (!matchesPassword(request.password(), row) || !active) {
            if (active) recordPasswordFailure(row, now);
            throw new PasswordRejected(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        if (request.role() != null && !request.role().equals(row.role()))
            throw error(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        repository.loginUpdateUsers(row.id());
        return issue(row.id());
    }

    private boolean matchesPassword(String raw, PasswordAccount account) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        boolean hasHash = account != null && account.hash().startsWith("$2");
        return passwords.matches(raw, hasHash ? account.hash() : dummyHash) && hasHash;
    }

    private boolean withinPasswordWindow(PasswordAccount account, Instant now) {
        return account.windowStartedAt() != null && account.windowStartedAt().plus(PASSWORD_FAILURE_WINDOW).isAfter(now);
    }

    private void checkPasswordBudget(PasswordAccount account, Instant now) {
        if (withinPasswordWindow(account, now) && account.failures() >= MAX_PASSWORD_FAILURES)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã thử mật khẩu quá nhiều lần. Vui lòng chờ 15 phút rồi thử lại.");
    }

    private void recordPasswordFailure(PasswordAccount account, Instant now) {
        boolean sameWindow = withinPasswordWindow(account, now);
        repository.recordPasswordFailure(account.id(), sameWindow ? account.failures() + 1 : 1,
            sameWindow ? account.windowStartedAt() : now);
    }

    // Failed password checks must commit their counters. Other exceptions still roll back.
    private static final class PasswordRejected extends ResponseStatusException {
        private PasswordRejected(HttpStatus status, String message) {
            super(status, message);
        }
    }

    private String randomSecret(int bytesCount) {
        byte[] bytes = new byte[bytesCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Session issue(long id) {
        String token = randomSecret(32);
        User account = user(id);
        long sessionHours = "STAFF".equals(account.role()) ? 24 : 8;
        Instant expiry = Instant.now().plus(sessionHours, ChronoUnit.HOURS);
        repository.issueDeleteAccountSessions();
        repository.issueInsertAccountSessions(id, digest(token), Timestamp.from(expiry));
        return new Session(token, expiry, account);
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

    @Transactional(noRollbackFor = PasswordRejected.class)
    public void changePassword(String header, ChangePassword request) {
        User current = authenticate(header, false);
        if (!"CUSTOMER".equals(current.role()) && !"STAFF".equals(current.role()))
            throw error(HttpStatus.FORBIDDEN, "Chức năng này dành cho khách hàng và nhân viên.");
        if (!request.newPassword().equals(request.confirmPassword()))
            throw error(HttpStatus.BAD_REQUEST, "Hai lần nhập mật khẩu mới không khớp.");

        var account = repository.passwordAccount(current.id());
        authenticate(header, false);
        Instant now = Instant.now();
        checkPasswordBudget(account, now);
        if (!matchesPassword(request.currentPassword(), account)) {
            recordPasswordFailure(account, now);
            throw new PasswordRejected(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }
        if (request.currentPassword().equals(request.newPassword()))
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");

        if (repository.updatePassword(current.id(), account.hash(), hashPassword(request.newPassword())) != 1)
            throw error(HttpStatus.CONFLICT, "Tài khoản vừa thay đổi. Vui lòng kiểm tra lại và thử lại.");
        repository.revokeOtherSessions(current.id(), digest(header.substring(7)));
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
        String initialPassword = randomSecret(24);
        long id = createUser(body.fullName(), body.phone(), body.email(), initialPassword, "STAFF");
        try {
            repository.createStaffInsertStaffs(id, body.identityCard().strip(), body.rank(), admin.id());
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Thông tin nhân viên đã được sử dụng.");
        }
        avatars.save(id, body.avatar(), true);
        saveStaffDetails(id, body.dateOfBirth(), body.birthYear(), body.gender(), body.experienceYears(), body.bio());
        var account = user(id);
        mailer.sendStaffCredentials(account.email(), initialPassword);
        return profile(account);
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
        return candidates(null);
    }

    public List<Map<String, Object>> candidates(String status) {
        var all = repository.candidatesFindStaffCandidates();
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return all;
        }
        return all.stream()
                .filter(c -> {
                    String s = Objects.toString(c.get("status"), "");
                    if ("APPLIED".equalsIgnoreCase(status)) {
                        return "PENDING".equalsIgnoreCase(s) || "APPLIED".equalsIgnoreCase(s);
                    }
                    return s.equalsIgnoreCase(status);
                })
                .toList();
    }

    public Map<String, Object> updateCandidateStatus(long id, Map<String, Object> body) {
        var data = findCandidate(id, true);
        String targetStatus = Objects.toString(body.get("status"), "").trim().toUpperCase();
        if (targetStatus.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "Trạng thái không được để trống.");
        }
        if ("APPLIED".equals(targetStatus)) targetStatus = "PENDING";

        if (List.of("PENDING", "ONBOARDING", "TRAINING", "EVALUATION").contains(targetStatus)) {
            data.put("status", targetStatus);
            data.put("subStatus", switch (targetStatus) {
                case "PENDING" -> "Chờ duyệt";
                case "ONBOARDING" -> "Đang tiếp nhận hồ sơ";
                case "TRAINING" -> "Đang học việc";
                case "EVALUATION" -> "Chờ xếp hạng";
                default -> "Chờ duyệt";
            });
        } else {
            data.put("subStatus", "REJECTED".equals(targetStatus) ? "Đã từ chối" : targetStatus);
            if (body.get("rejectionReason") != null) {
                data.put("rejectionReason", body.get("rejectionReason"));
            }
        }
        if (body.get("note") != null) {
            data.put("note", body.get("note"));
        }
        saveCandidate(id, data);
        return data;
    }

    public Map<String, Object> applyCandidate(Map<String, Object> input) {
        var normalized = new LinkedHashMap<>(input);
        if (normalized.get("cccd") == null && normalized.get("identityCard") != null) {
            normalized.put("cccd", normalized.get("identityCard"));
        }
        if (normalized.get("phone") != null) {
            normalized.put("phone", normalized.get("phone").toString().replaceAll("\\s+", ""));
        }
        if (normalized.get("cccd") != null) {
            normalized.put("cccd", normalized.get("cccd").toString().replaceAll("\\s+", ""));
        }
        if (normalized.get("services") == null && normalized.get("registeredServices") != null) {
            normalized.put("services", normalized.get("registeredServices"));
        }
        if (normalized.get("source") == null || normalized.get("source").toString().isBlank()) {
            normalized.put("source", "Website CleanMaster");
        }
        if (normalized.get("note") == null) {
            normalized.put("note", "Đăng ký ứng tuyển trực tuyến qua website");
        }
        return newCandidate(normalized);
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
        var normalizedInput = new LinkedHashMap<>(input);
        if (normalizedInput.get("cccd") == null && normalizedInput.get("identityCard") != null) {
            normalizedInput.put("cccd", normalizedInput.get("identityCard"));
        }
        if (normalizedInput.get("phone") != null) {
            normalizedInput.put("phone", normalizedInput.get("phone").toString().replaceAll("\\s+", ""));
        }
        if (normalizedInput.get("cccd") != null) {
            normalizedInput.put("cccd", normalizedInput.get("cccd").toString().replaceAll("\\s+", ""));
        }
        if (normalizedInput.get("services") == null && normalizedInput.get("registeredServices") != null) {
            normalizedInput.put("services", normalizedInput.get("registeredServices"));
        }
        if (normalizedInput.get("source") == null || normalizedInput.get("source").toString().isBlank()) {
            normalizedInput.put("source", "Website CleanMaster");
        }
        if (normalizedInput.get("note") == null) {
            normalizedInput.put("note", "Đăng ký ứng tuyển trực tuyến qua website");
        }

        var data = new LinkedHashMap<String, Object>();
        for (String key : List.of("fullName", "phone", "email", "cccd", "gender", "area", "source", "note")) {
            data.put(key, text(normalizedInput, key, key.equals("note") ? 2000 : key.equals("fullName") ? 160 : 254, !key.equals("note")));
        }

        if (!text(normalizedInput, "phone", 30, true).matches("(?:0[0-9]{9}|\\+84[0-9]{9})") || !text(normalizedInput, "cccd", 30, true).matches("[0-9]{12}") || !text(normalizedInput, "email", 254, true).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw error(HttpStatus.BAD_REQUEST, "Số điện thoại, email hoặc CCCD không hợp lệ.");

        String genderVal = Objects.toString(data.get("gender"), "").trim();
        if (genderVal.equalsIgnoreCase("male")) genderVal = "Nam";
        else if (genderVal.equalsIgnoreCase("female")) genderVal = "Nữ";
        else if (genderVal.equalsIgnoreCase("other")) genderVal = "Khác";
        if (genderVal.toLowerCase().startsWith("n") && (genderVal.contains("ữ") || genderVal.toLowerCase().contains("u"))) {
            genderVal = "Nữ";
        } else if (genderVal.equalsIgnoreCase("Khác") || genderVal.equalsIgnoreCase("Other")) {
            genderVal = "Khác";
        } else {
            genderVal = "Nam";
        }
        data.put("gender", genderVal);

        var fullBirth = staffBirthDate(Objects.toString(normalizedInput.get("dateOfBirth"), ""));
        Object yearObj = fullBirth == null ? normalizedInput.get("birthYear") : fullBirth.getYear();
        Integer year = null;
        if (yearObj instanceof Number n) {
            year = n.intValue();
        } else if (yearObj instanceof String s && !s.isBlank()) {
            try { year = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        if (fullBirth != null)
            data.put("dateOfBirth", fullBirth.toString());
        if (year == null || year < 1940 || year > java.time.Year.now().getValue() - 18)
            throw error(HttpStatus.BAD_REQUEST, "Ứng viên phải từ 18 tuổi; kiểm tra năm sinh.");
        data.put("birthYear", year);

        Object experience = normalizedInput.get("experienceYears");
        if (experience instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 80)
            data.put("experienceYears", n);

        var services = strings(normalizedInput, "services");
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
        String initialPassword = randomSecret(24);
        long staffId = createUser((String) data.get("fullName"), (String) data.get("phone"), (String) data.get("email"), initialPassword, "STAFF");
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
        avatars.save(staffId, request.avatar(), true);
        saveStaffDetails(staffId, Objects.toString(data.get("dateOfBirth"), ""), data.get("birthYear") instanceof Number n ? n.intValue() : null, null, data.get("experienceYears") instanceof Number n ? n.doubleValue() : null, null);
        data.put("staffId", staffId);
        data.put("rank", request.rank());
        data.put("subStatus", "Đã chuyển thành nhân viên chính thức");
        data.put("credentialsEmailSent", true);
        saveCandidate(id, data);
        repository.provisionUpdateStaffCandidates(staffId, id);
        mailer.sendStaffCredentials(user(staffId).email(), initialPassword);
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
        if (request.avatar() != null) avatars.save(id, request.avatar(), true);
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
