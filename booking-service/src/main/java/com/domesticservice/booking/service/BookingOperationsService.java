package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.booking.repository.BookingOperationsSqlRepository;

@Service
public class BookingOperationsService {

    private final BookingOperationsSqlRepository repository;

    private final ObjectMapper json;

    final ServiceHttpClient identity, catalog;

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final Map<String, String> AREAS = Map.ofEntries(Map.entry("quan-1", "Quận 1"), Map.entry("quan-2", "Quận 2"), Map.entry("quan-3", "Quận 3"), Map.entry("quan-7", "Quận 7"), Map.entry("quan-tan-binh", "Quận Tân Bình"), Map.entry("quan-binh-thanh", "Quận Bình Thạnh"));

    public BookingOperationsService(BookingOperationsSqlRepository repository, ObjectMapper json, @Value("${clients.identity.base-url}") String identityUrl, @Value("${clients.catalog.base-url}") String catalogUrl) {
        this.repository = repository;
        this.json = json;
        identity = new ServiceHttpClient(identityUrl);
        catalog = new ServiceHttpClient(catalogUrl);
    }

    public ServiceHttpClient identityClient() {
        return identity;
    }

    public ServiceHttpClient catalogClient() {
        return catalog;
    }

    public Map<String, Object> actor(String token, String... roles) {
        return identity.authenticate(token, roles);
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    static String text(Map<String, Object> b, String key, int max, boolean required) {
        String v = Objects.toString(b.get(key), "").strip();
        if (v.length() > max || required && v.isBlank())
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra " + key + ".");
        return v;
    }

    static long num(Map<String, Object> b, String key, long fallback, long min, long max) {
        try {
            long n = b.get(key) == null ? fallback : Long.parseLong(b.get(key).toString());
            if (n < min || n > max)
                throw error(HttpStatus.BAD_REQUEST, "Giá trị " + key + " không hợp lệ.");
            return n;
        } catch (NumberFormatException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Giá trị " + key + " không hợp lệ.");
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> l) || l.size() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Danh sách không hợp lệ.");
        for (var entry : l) if (!(entry instanceof Map))
            throw error(HttpStatus.BAD_REQUEST, "Danh sách không hợp lệ.");
        return (List<Map<String, Object>>) l;
    }

    Map<String, Object> object(Object value) {
        return json.readValue(Objects.toString(value, "{}"), new TypeReference<Map<String, Object>>() {
        });
    }

    List<Map<String, Object>> objects(Object value) {
        return json.readValue(Objects.toString(value, "[]"), new TypeReference<List<Map<String, Object>>>() {
        });
    }

    void lockStaff(long id) {
        repository.lockStaffInsertStaffScheduleSettings(id);
        repository.lockStaffFindStaffScheduleSettingsForUpdate(Long.class, id);
    }

    public Map<Long, Map<String, Object>> services(String token) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        int pageNumber = 0, totalPages = 1;
        do {
            var page = catalog.get("/api/v1/catalog/services?size=100&page=" + pageNumber, token);
            for (var item : maps(page.get("items"))) result.put(((Number) item.get("id")).longValue(), item);
            totalPages = ((Number) page.getOrDefault("totalPages", 1)).intValue();
            pageNumber++;
        } while (pageNumber < totalPages);
        return result;
    }

    public List<Map<String, Object>> capabilities(String token, long staff, boolean admin) {
        var lookup = services(token);
        var list = repository.capabilitiesFindStaffServiceCapabilities(staff);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : list) {
            long sid = ((Number) row.get("service_id")).longValue();
            var item = new LinkedHashMap<String, Object>();
            item.put("id", row.get("id"));
            item.put("staffId", staff);
            item.put("serviceId", sid);
            item.put("serviceName", lookup.containsKey(sid) ? lookup.get(sid).get("name") : "Dịch vụ không còn hoạt động");
            item.put("experienceYears", row.get("experience_years"));
            item.put("skillLevel", row.get("skill_level"));
            item.put("status", row.get("status"));
            item.put("approvedAt", instant(row.get("approved_at")));
            item.put("approvedBy", admin ? row.get("approved_by") : null);
            item.put("staffMayViewApproval", false);
            item.put("staffMayViewNote", row.get("staff_may_view_note"));
            item.put("note", admin || Boolean.TRUE.equals(row.get("staff_may_view_note")) ? row.get("note") : null);
            item.put("validFrom", instant(row.get("valid_from")));
            item.put("validUntil", instant(row.get("valid_until")));
            item.put("scope", List.of());
            result.add(item);
        }
        return result;
    }

    static String instant(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant().toString();
    }

    public List<Map<String, Object>> restrictions(long staff) {
        return repository.restrictionsFindStaffRestrictions(staff);
    }

    public Map<String, Object> schedule(long staff) {
        var settings = repository.scheduleFindStaffScheduleSettings(staff);
        long revision = settings.isEmpty() ? 0 : ((Number) settings.getFirst().get("revision")).longValue();
        var rows = repository.scheduleFindStaffAvailabilities(staff);
        List<Map<String, Object>> days = new ArrayList<>();
        LocalDate today = WeeklySchedulePolicy.weekStart(Instant.now());
        for (int i = 0; i < 14; i++) {
            LocalDate date = today.plusDays(i);
            List<Map<String, Object>> intervals = new ArrayList<>();
            for (var row : rows) {
                boolean applies = row.get("available_date") != null ? ((java.sql.Date) row.get("available_date")).toLocalDate().equals(date) : Objects.equals(row.get("day_of_week"), (short) (date.getDayOfWeek().getValue() % 7)) || Objects.equals(row.get("day_of_week"), date.getDayOfWeek().getValue() % 7);
                if (applies) {
                    var start = ((Time) row.get("start_time")).toLocalTime();
                    var end = ((Time) row.get("end_time")).toLocalTime();
                    intervals.add(Map.of("startsAt", date.atTime(start).atZone(ZONE).toOffsetDateTime().toString(), "endsAt", date.atTime(end).atZone(ZONE).toOffsetDateTime().toString()));
                }
            }
            days.add(Map.of("date", date.toString(), "intervals", intervals));
        }
        var areas = repository.scheduleFindStaffAreas(String.class, staff);
        var areaIds = areas.stream().map(BookingOperationsService::district).toList();
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("days", days);
        saved.put("registeredWeekStarts", repository.scheduleFindStaffScheduleWeeks(String.class, staff));
        saved.put("areaIds", areaIds);
        saved.put("matchNotifications", settings.isEmpty() || Boolean.TRUE.equals(settings.getFirst().get("match_notifications")));
        saved.put("revision", revision);
        saved.put("updatedAt", settings.isEmpty() ? Instant.now().toString() : instant(settings.getFirst().get("updated_at")));
        var bookings = repository.scheduleFindBookings(staff);
        return Map.of("saved", saved, "bookings", bookings, "unknownBookingCodes", List.of());
    }

    @Transactional
    public Map<String, Object> saveSchedule(String token, Map<String, Object> body) {
        long staff = ServiceHttpClient.userId(actor(token, "STAFF"));
        lockStaff(staff);
        long revision = num(body, "revision", -1, 0, Long.MAX_VALUE), current = repository.saveScheduleFindStaffScheduleSettings(Long.class, staff);
        if (revision != current)
            throw error(HttpStatus.CONFLICT, "Lịch đã được cập nhật ở phiên khác. Vui lòng tải lại.");
        Instant now = Instant.now();
        LocalDate first = WeeklySchedulePolicy.weekStart(now), week;
        try {
            week = LocalDate.parse(text(body, "weekStart", 10, true));
        } catch (DateTimeException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Tuần không hợp lệ.");
        }
        if (!week.equals(first) && !week.equals(first.plusWeeks(1)))
            throw error(HttpStatus.BAD_REQUEST, "Chỉ đăng ký tuần hiện tại hoặc tuần kế tiếp.");
        var days = maps(body.get("days"));
        if (days.size() != 7)
            throw error(HttpStatus.BAD_REQUEST, "Cần xác nhận đủ 7 ngày trong tuần.");
        Set<LocalDate> used = new HashSet<>();
        List<WeeklySchedulePolicy.Period> periods = new ArrayList<>();
        for (var day : days) {
            LocalDate date;
            try {
                date = LocalDate.parse(text(day, "date", 10, true));
            } catch (DateTimeException ex) {
                throw error(HttpStatus.BAD_REQUEST, "Ngày không hợp lệ.");
            }
            if (date.isBefore(week) || date.isAfter(week.plusDays(6)) || !used.add(date))
                throw error(HttpStatus.BAD_REQUEST, "Ngày phải thuộc đúng tuần, không trùng ngày.");
            for (var interval : maps(day.get("intervals"))) {
                var a = parseTime(text(interval, "startsAt", 50, true));
                var b = parseTime(text(interval, "endsAt", 50, true));
                validateWindow(a, b, false);
                if (!a.atZone(ZONE).toLocalDate().equals(date))
                    throw error(HttpStatus.BAD_REQUEST, "Khoảng rảnh phải thuộc đúng ngày.");
                if (periods.stream().anyMatch(p -> a.isBefore(p.end()) && b.isAfter(p.start())))
                    throw error(HttpStatus.BAD_REQUEST, "Khoảng rảnh bị chồng lấn.");
                periods.add(new WeeklySchedulePolicy.Period(a, b));
            }
        }
        var confirmed = stringList(body.get("confirmedDates"));
        var required = java.util.stream.IntStream.range(0, 7).mapToObj(week::plusDays).filter(date -> !date.isBefore(now.atZone(ZONE).toLocalDate())).map(LocalDate::toString).toList();
        if (!new HashSet<>(confirmed).containsAll(required) || confirmed.stream().anyMatch(date -> !used.stream().map(LocalDate::toString).toList().contains(date)))
            throw error(HttpStatus.BAD_REQUEST, "Chọn ca làm hoặc Nghỉ cho từng ngày còn lại trong tuần trước khi xác nhận.");
        var oldRows = repository.saveScheduleFindStaffAvailabilities(staff, week, week.plusDays(6));
        var before = oldRows.stream().map(p -> {
            var date = ((java.sql.Date) p.get("available_date")).toLocalDate();
            return new WeeklySchedulePolicy.Period(date.atTime(((Time) p.get("start_time")).toLocalTime()).atZone(ZONE).toInstant(), date.atTime(((Time) p.get("end_time")).toLocalTime()).atZone(ZONE).toInstant());
        }).toList();
        WeeklySchedulePolicy.protectPast(before, periods, now);
        List<String> areas = validatedAreas(token, body.get("areaIds"));
        if (areas.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Chọn ít nhất một phường/xã nhận việc.");
        var bookings = repository.saveScheduleFindBookings(staff);
        for (var b : bookings) {
            Instant a = ((Timestamp) b.get("starts_at")).toInstant(), z = ((Timestamp) b.get("ends_at")).toInstant();
            var date = a.atZone(ZONE).toLocalDate();
            if (!date.isBefore(week) && !date.isAfter(week.plusDays(6)) && !WeeklySchedulePolicy.covers(periods, a, z))
                throw error(HttpStatus.CONFLICT, "Lịch mới không bao phủ ca đã nhận.");
            String area = Objects.toString(b.get("district"), "");
            if (modernArea(area) && !areas.contains(area))
                throw error(HttpStatus.CONFLICT, "Phải giữ phường/xã của ca đã nhận: " + area);
        }
        repository.saveScheduleDeleteStaffAvailabilities(staff, week, week.plusDays(6));
        for (var p : periods) repository.saveScheduleInsertStaffAvailabilities(staff, p.start().atZone(ZONE).toLocalDate(), p.start().atZone(ZONE).toLocalTime(), p.end().atZone(ZONE).toLocalTime());
        saveAreas(staff, areas);
        repository.saveScheduleInsertStaffScheduleWeeks(staff, week);
        repository.saveScheduleUpdateStaffScheduleSettings(!Boolean.FALSE.equals(body.get("matchNotifications")), staff);
        return schedule(staff);
    }

    boolean weekRegistered(long staff, Instant start) {
        var week = WeeklySchedulePolicy.weekStart(start);
        return Boolean.TRUE.equals(repository.weekRegisteredExistsStaffScheduleWeeks(Boolean.class, staff, week));
    }

    static boolean modernArea(String value) {
        return value.startsWith("Phường ") || value.startsWith("Xã ") || value.startsWith("Đặc khu ");
    }

    List<String> validatedAreas(String token, Object raw) {
        var areas = stringList(raw);
        if (areas.size() > 30)
            throw error(HttpStatus.BAD_REQUEST, "Chọn tối đa 30 phường/xã.");
        var directory = identity.get("/api/v1/identity/administrative-units/hcm", token);
        var names = mapsDirectory(directory.get("items"));
        if (areas.stream().anyMatch(a -> !names.contains(a)))
            throw error(HttpStatus.BAD_REQUEST, "Chỉ chọn phường/xã mới trong danh mục TP. Hồ Chí Minh.");
        return areas;
    }

    static Set<String> mapsDirectory(Object raw) {
        Set<String> names = new HashSet<>();
        if (raw instanceof List<?> items)
            for (var item : items) if (item instanceof Map<?, ?> map)
                names.add(Objects.toString(map.get("name"), ""));
        return names;
    }

    static List<String> stringList(Object body) {
        if (!(body instanceof List<?> list) || list.size() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Danh sách không hợp lệ.");
        return list.stream().map(x -> {
            String s = Objects.toString(x, "");
            if (s.isBlank() || s.length() > 100)
                throw error(HttpStatus.BAD_REQUEST, "Giá trị danh sách không hợp lệ.");
            return s;
        }).distinct().toList();
    }

    void saveAreas(long staff, List<String> areas) {
        repository.saveAreasDeleteStaffAreas(staff);
        for (int i = 0; i < areas.size(); i++) repository.saveAreasInsertStaffAreas(staff, district(AREAS.getOrDefault(areas.get(i), areas.get(i))), i == 0);
    }

    static Instant parseTime(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Thời gian phải có múi giờ.");
        }
    }

    public static void validateWindow(Instant start, Instant end, boolean future) {
        var s = start.atZone(ZONE);
        var e = end.atZone(ZONE);
        if (!end.isAfter(start) || !s.toLocalDate().equals(e.toLocalDate()) || s.getHour() < 8 || e.toLocalTime().isAfter(LocalTime.of(20, 0)) || s.getMinute() % 30 != 0 || e.getMinute() % 30 != 0 || s.getSecond() != 0 || e.getSecond() != 0 || s.getNano() != 0 || e.getNano() != 0)
            throw error(HttpStatus.BAD_REQUEST, "Thời gian phải trong 08:00–20:00, bước 30 phút, kết thúc sau bắt đầu.");
        if (future && (start.isBefore(Instant.now()) || s.toLocalDate().isAfter(LocalDate.now(ZONE).plusDays(6))))
            throw error(HttpStatus.BAD_REQUEST, "Chỉ đặt trong 7 ngày tiếp theo.");
    }

    public Map<String, Object> staffOperations(String token, long staff) {
        actor(token, "ADMIN");
        boolean exists = identity.list("/api/v1/identity/admin/staff", token).stream().anyMatch(u -> ServiceHttpClient.userId(u) == staff);
        if (!exists)
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên.");
        return Map.of("capabilities", capabilities(token, staff, true), "restrictions", restrictions(staff), "availability", schedule(staff), "completedBookings", repository.staffOperationsCountBookingAssignments(Long.class, staff), "cancelledBookings", repository.staffOperationsCountBookingAssignments2(Long.class, staff), "averageRating", repository.staffOperationsFindReviews(Double.class, staff), "reviewCount", repository.staffOperationsCountReviews(Long.class, staff));
    }

    public Map<String, Object> staffPeriodOperations(String token, long staff, String period) {
        var result = new LinkedHashMap<>(staffOperations(token, staff));
        if ("ALL".equals(period))
            return result;
        if (!List.of("CURRENT", "PREVIOUS").contains(period))
            throw error(HttpStatus.BAD_REQUEST, "Kỳ thống kê không hợp lệ.");
        var first = LocalDate.now(ZONE).withDayOfMonth(1).minusMonths("PREVIOUS".equals(period) ? 1 : 0).atStartOfDay(ZONE).toOffsetDateTime();
        var end = first.plusMonths(1);
        result.put("completedBookings", repository.staffPeriodOperationsCountBookingAssignments(Long.class, staff, first, end));
        result.put("cancelledBookings", repository.staffPeriodOperationsCountBookingAssignments2(Long.class, staff, first, end));
        result.put("averageRating", repository.staffPeriodOperationsFindReviews(Double.class, staff, first, end));
        result.put("reviewCount", repository.staffPeriodOperationsCountReviews(Long.class, staff, first, end));
        return result;
    }

    @Transactional
    public Map<String, Object> saveStaffOperations(String token, long staff, Map<String, Object> body) {
        long admin = ServiceHttpClient.userId(actor(token, "ADMIN"));
        staffOperations(token, staff);
        lockStaff(staff);
        var active = services(token);
        if (body.containsKey("skillIds")) {
            var selected = stringList(body.get("skillIds"));
            repository.saveStaffOperationsUpdateStaffServiceCapabilities(staff);
            for (var raw : selected) {
                long sid;
                try {
                    sid = Long.parseLong(raw);
                } catch (NumberFormatException ex) {
                    throw error(HttpStatus.BAD_REQUEST, "ID dịch vụ không hợp lệ.");
                }
                if (!active.containsKey(sid))
                    throw error(HttpStatus.BAD_REQUEST, "Dịch vụ không tồn tại hoặc ngừng hoạt động.");
                repository.saveStaffOperationsInsertStaffServiceCapabilities(staff, sid, admin, admin);
            }
        }
        if (body.containsKey("areaIds"))
            saveAreas(staff, validatedAreas(token, body.get("areaIds")));
        if (body.containsKey("restrictions")) {
            for (var r : maps(body.get("restrictions"))) {
                long id = num(r, "id", 0, 0, Long.MAX_VALUE);
                String type = text(r, "type", 30, true);
                if (!List.of("WARNING", "SERVICE_LIMIT", "SUSPENSION").contains(type))
                    throw error(HttpStatus.BAD_REQUEST, "Loại hạn chế không hợp lệ.");
                String state = text(r, "status", 20, true);
                if (!List.of("ACTIVE", "EXPIRED", "REVOKED").contains(state))
                    throw error(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
                String reason = text(r, "reason", 2000, true);
                var start = parseTime(text(r, "startsAt", 50, true));
                var end = r.get("endsAt") == null ? null : parseTime(text(r, "endsAt", 50, true));
                if (end != null && !end.isAfter(start))
                    throw error(HttpStatus.BAD_REQUEST, "Thời hạn không hợp lệ.");
                if (id == 0)
                    repository.saveStaffOperationsInsertStaffRestrictions(staff, type, r.get("serviceId"), reason, Timestamp.from(start), end == null ? null : Timestamp.from(end), state, admin);
                else if (repository.saveStaffOperationsUpdateStaffRestrictions(type, reason, Timestamp.from(start), end == null ? null : Timestamp.from(end), state, id, staff) == 0)
                    throw error(HttpStatus.NOT_FOUND, "Không tìm thấy hạn chế của nhân viên.");
            }
        }
        repository.saveStaffOperationsUpdateStaffScheduleSettings(staff);
        return staffOperations(token, staff);
    }

    static String city(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT).strip().replaceFirst("^(tp\\.?\\s*|thanh\\s+pho\\s+)", "").replaceAll("\\s+", " ");
        return switch(normalized) {
            case "hcm", "sai gon" ->
                "ho chi minh";
            case "hn" ->
                "ha noi";
            default ->
                normalized;
        };
    }

    static String district(String value) {
        String s = value.strip();
        return s.matches("(?iu)^Quận [^0-9].*") ? s.substring(5).strip() : s;
    }

    // Historical snapshots retain their district; modern two-level addresses use ward.
    static String location(Map<String, Object> address) {
        String ward = Objects.toString(address.get("ward"), "").strip();
        if (modernArea(ward))
            return ward;
        String old = Objects.toString(address.get("district"), "").strip();
        return old.isBlank() ? ward : district(old);
    }
}
