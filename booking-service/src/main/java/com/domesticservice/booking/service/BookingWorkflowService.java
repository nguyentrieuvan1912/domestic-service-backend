package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import static com.domesticservice.booking.service.BookingOperationsService.*;
import com.domesticservice.booking.repository.BookingWorkflowSqlRepository;

@Service
public class BookingWorkflowService {

    private final BookingOperationsService ops;

    private final BookingWorkflowSqlRepository repository;

    private final ObjectMapper json;

    public BookingWorkflowService(BookingOperationsService ops, BookingWorkflowSqlRepository repository, ObjectMapper json) {
        this.ops = ops;
        this.repository = repository;
        this.json = json;
    }

    private Map<String, Object> row(long id, boolean lock) {
        var rows = repository.rowFindBookingsForUpdate(lock, id);
        if (rows.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt dịch vụ.");
        return rows.getFirst();
    }

    private void owned(Map<String, Object> actor, Map<String, Object> booking) {
        long user = ServiceHttpClient.userId(actor);
        if ("ADMIN".equals(actor.get("role")) || "CUSTOMER".equals(actor.get("role")) && ((Number) booking.get("customer_id")).longValue() == user)
            return;
        if ("STAFF".equals(actor.get("role")) && Boolean.TRUE.equals(repository.ownedExistsBookingAssignments(Boolean.class, booking.get("id"), user)))
            return;
        throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn thuộc tài khoản của bạn.");
    }

    Map<String, Object> bookingView(Map<String, Object> b) {
        var service = ops.object(b.get("service_snapshot"));
        var address = ops.object(b.get("address_snapshot"));
        var result = new LinkedHashMap<String, Object>();
        result.put("serverNow", Instant.now().toString());
        result.put("staffResponseDeadline", instant(b.get("staff_response_deadline")));
        result.put("staffWaitState", b.get("staff_wait_state"));
        result.put("staffWaitRound", b.get("staff_wait_round"));
        result.put("quoteId", b.get("quote_id"));
        result.put("pricingConfirmed", b.get("pricing_confirmed"));
        result.put("id", b.get("id"));
        result.put("bookingCode", b.get("booking_code"));
        result.put("customerId", b.get("customer_id"));
        result.put("serviceId", b.get("service_id"));
        result.put("packageId", b.get("package_id"));
        result.put("addressId", b.get("address_id"));
        result.put("mode", b.get("booking_mode"));
        result.put("requiredStaffCount", b.get("required_staff_count"));
        result.put("startsAt", instant(b.get("starts_at")));
        result.put("endsAt", instant(b.get("ends_at")));
        result.put("bookingDate", ((Timestamp) b.get("starts_at")).toInstant().atZone(ZONE).toLocalDate().toString());
        result.put("startTime", ((Timestamp) b.get("starts_at")).toInstant().atZone(ZONE).toLocalTime().toString());
        result.put("endTime", ((Timestamp) b.get("ends_at")).toInstant().atZone(ZONE).toLocalTime().toString());
        result.put("status", b.get("status"));
        result.put("packagePrice", b.get("base_amount"));
        result.put("addOnsTotal", b.get("add_on_amount"));
        result.put("discountAmount", b.get("discount_amount"));
        result.put("totalAmount", b.get("total_amount"));
        result.put("promotionCode", b.get("promotion_code"));
        result.put("paymentMethod", b.get("payment_method"));
        result.put("paymentStatus", b.get("payment_status"));
        result.put("notes", b.get("notes"));
        result.put("createdAt", instant(b.get("created_at")));
        result.put("updatedAt", instant(b.get("updated_at")));
        result.put("cancelledAt", instant(b.get("cancelled_at")));
        result.put("cancellationReason", b.get("cancel_reason"));
        result.put("cancelledBy", b.get("cancelled_by_type"));
        result.put("service", service);
        result.put("address", address);
        result.put("addOns", repository.bookingViewFindBookingAddOns(b.get("id")));
        var assignments = repository.bookingViewFindBookingAssignments(b.get("id"));
        for (var assignment : assignments) if (assignment.containsKey("staffSnapshot"))
            assignment.put("staffSnapshot", ops.object(assignment.get("staffSnapshot")));
        result.put("assignments", assignments);
        result.put("staffId", assignments.stream().filter(a -> List.of("ACCEPTED", "COMPLETED").contains(a.get("status"))).map(a -> a.get("staffId")).findFirst().orElse(null));
        var snapshots = repository.bookingViewExistsBookingAssignments(b.get("id"));
        if (!snapshots.isEmpty())
            result.put("staff", ops.object(snapshots.getFirst().get("staff_snapshot")));
        result.put("customServiceData", service.getOrDefault("answers", Map.of()));
        return result;
    }

    public Map<String, Object> detail(String token, long id) {
        var actor = ops.actor(token, "CUSTOMER", "STAFF", "ADMIN");
        var b = row(id, false);
        owned(actor, b);
        return bookingView(b);
    }

    public List<Map<String, Object>> list(String token) {
        var user = ops.actor(token, "CUSTOMER", "STAFF", "ADMIN");
        return repository.listFindBookings(user).stream().map(this::bookingView).toList();
    }

    public List<Map<String, Object>> history(String token, long id) {
        detail(token, id);
        return repository.historyFindBookingStatusHistories(id);
    }

    List<String> eligibility(long staff, Map<String, Object> b, Map<String, Object> profile) {
        if (!ops.weekRegistered(staff, ((Timestamp) b.get("starts_at")).toInstant()))
            return List.of("SCHEDULE_REQUIRED");
        List<String> reasons = new ArrayList<>();
        if ("MODE_A".equals(b.get("booking_mode")) && (!"WAITING".equals(b.get("staff_wait_state")) || b.get("staff_response_deadline") == null || !((Timestamp) b.get("staff_response_deadline")).toInstant().isAfter(Instant.now())))
            reasons.add("INVITATION_EXPIRED");
        long service = ((Number) b.get("service_id")).longValue();
        Instant start = ((Timestamp) b.get("starts_at")).toInstant(), end = ((Timestamp) b.get("ends_at")).toInstant();
        var location = ops.object(b.get("address_snapshot"));
        String district = BookingOperationsService.location(location), city = city(Objects.toString(location.getOrDefault("city", location.get("province")), ""));
        if (profile == null)
            reasons.add("INACTIVE");
        else {
            if (!Boolean.TRUE.equals(profile.get("isOnline")))
                reasons.add("OFFLINE");
            if (!"AVAILABLE".equals(profile.getOrDefault("staffStatus", profile.get("status"))))
                reasons.add("INACTIVE");
        }
        if (start.isBefore(Instant.now()))
            reasons.add("PAST_START");
        Set<Long> requiredServices = new LinkedHashSet<>();
        requiredServices.add(service);
        var snapshot = ops.object(b.get("service_snapshot"));
        if (snapshot.get("relatedServices") instanceof List<?> related) {
            for (var item : related) {
                if (item instanceof Map<?, ?> map && map.get("id") instanceof Number id)
                    requiredServices.add(id.longValue());
            }
        }
        for (long requiredService : requiredServices) {
            if (!Boolean.TRUE.equals(repository.eligibilityExistsStaffServiceCapabilities(Boolean.class, staff, requiredService, Timestamp.from(start), Timestamp.from(end))) && !reasons.contains("CAPABILITY_MISSING"))
                reasons.add("CAPABILITY_MISSING");
            if (Boolean.TRUE.equals(repository.eligibilityExistsStaffRestrictions(Boolean.class, staff, requiredService, Timestamp.from(end), Timestamp.from(start))) && !reasons.contains("RESTRICTION"))
                reasons.add("RESTRICTION");
        }
        if (repository.eligibilityFindStaffAreas(String.class, staff, district).stream().noneMatch(province -> city(province).equals(city)))
            reasons.add("AREA_OUTSIDE");
        if (!Boolean.TRUE.equals(repository.eligibilityExistsStaffAvailabilities(Boolean.class, staff, java.sql.Date.valueOf(start.atZone(ZONE).toLocalDate()), start.atZone(ZONE).getDayOfWeek().getValue() % 7, java.sql.Time.valueOf(start.atZone(ZONE).toLocalTime()), java.sql.Time.valueOf(end.atZone(ZONE).toLocalTime()))))
            reasons.add("OUTSIDE_AVAILABILITY");
        if (Boolean.TRUE.equals(repository.eligibilityExistsBookings(Boolean.class, staff, b.get("id"), Timestamp.from(end.plusSeconds(3600)), Timestamp.from(start.minusSeconds(3600)))))
            reasons.add("BOOKING_CONFLICT");
        long required = ((Number) b.get("required_staff_count")).longValue(), count = repository.eligibilityCountBookingAssignments(Long.class, b.get("id"));
        if (count >= required)
            reasons.add("POSITION_FULL");
        if ("MODE_A".equals(b.get("booking_mode")) && !Boolean.TRUE.equals(repository.eligibilityExistsBookingAssignments(Boolean.class, b.get("id"), staff)))
            reasons.add("NOT_INVITED");
        return reasons;
    }

    private static final Map<String, String> REASONS = Map.ofEntries(Map.entry("INVITATION_EXPIRED", "Hết thời gian nhận đơn. Vui lòng chú ý lời mời để có doanh thu tốt hơn."), Map.entry("SCHEDULE_REQUIRED", "Bạn cần đăng ký đầy đủ lịch làm việc trong tuần trước khi được xét nhận đơn."), Map.entry("INACTIVE", "Tài khoản hoặc trạng thái làm việc không phù hợp."), Map.entry("OFFLINE", "Bạn đang ngoại tuyến."), Map.entry("PAST_START", "Ca đã qua giờ bắt đầu."), Map.entry("CAPABILITY_MISSING", "Chưa có năng lực được Admin xác nhận."), Map.entry("RESTRICTION", "Bạn đang bị hạn chế nhận dịch vụ này."), Map.entry("AREA_OUTSIDE", "Ca nằm ngoài khu vực hoạt động."), Map.entry("OUTSIDE_AVAILABILITY", "Ca nằm ngoài lịch rảnh đã lưu."), Map.entry("BOOKING_CONFLICT", "Trùng ca hoặc không đủ khoảng đệm 1 giờ."), Map.entry("POSITION_FULL", "Đã đủ nhân viên nhận ca."), Map.entry("NOT_INVITED", "Bạn không được mời vào ca Mode A."));

    Map<String, Object> matchItem(long staff, Map<String, Object> b, Map<String, Object> profile) {
        var service = ops.object(b.get("service_snapshot"));
        var address = ops.object(b.get("address_snapshot"));
        long occupied = repository.matchItemCountBookingAssignments(Long.class, b.get("id"));
        long required = ((Number) b.get("required_staff_count")).longValue();
        var opportunity = new LinkedHashMap<String, Object>();
        opportunity.put("id", b.get("id"));
        opportunity.put("staffResponseDeadline", instant(b.get("staff_response_deadline")));
        opportunity.put("bookingCode", b.get("booking_code"));
        opportunity.put("serviceId", b.get("service_id"));
        opportunity.put("serviceName", Objects.toString(service.get("name"), Objects.toString(service.get("serviceName"), "")));
        opportunity.put("district", BookingOperationsService.location(address));
        opportunity.put("startsAt", instant(b.get("starts_at")));
        opportunity.put("endsAt", instant(b.get("ends_at")));
        opportunity.put("packageTitle", Objects.toString(service.get("packageName"), ""));
        opportunity.put("netIncome", 0);
        opportunity.put("incomePendingPolicy", true);
        opportunity.put("mode", "MODE_A".equals(b.get("booking_mode")) ? "A" : "B");
        opportunity.put("positionsRequired", required);
        opportunity.put("positionsRemaining", Math.max(0, required - occupied));
        opportunity.put("requiredCapabilities", List.of());
        if ("MODE_A".equals(b.get("booking_mode")))
            opportunity.put("invitedStaffId", staff);
        StaffJobPresentation.summary(opportunity, b, service, address);
        var reasons = eligibility(staff, b, profile);
        return Map.of("opportunity", opportunity, "eligibility", Map.of("allowed", reasons.isEmpty(), "reasons", reasons.stream().map(r -> Map.of("code", r, "message", REASONS.get(r))).toList(), "checks", List.of()));
    }

    public Map<String, Object> matching(String token) {
        var user = ops.actor(token, "STAFF");
        long staff = ServiceHttpClient.userId(user);
        var profile = ops.identityClient().get("/api/v1/identity/auth/profile", token);
        var candidates = repository.matchingExistsBookings(staff, staff);
        // Mode B is a private, eligible job feed. Invitations remain visible with their reason.
        var items = candidates.stream().map(b -> matchItem(staff, b, profile))
            .filter(item -> "A".equals(((Map<?, ?>) item.get("opportunity")).get("mode"))
                || Boolean.TRUE.equals(((Map<?, ?>) item.get("eligibility")).get("allowed")))
            .toList();
        return Map.of("items", items, "checkedAt", Instant.now().toString(), "revision", 0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> opportunityDetail(String token, long id) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        var b = row(id, false);
        if (!Boolean.TRUE.equals(b.get("pricing_confirmed")) || !List.of("PENDING", "MATCHING", "STAFF_ASSIGNED").contains(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Ca không còn mở để nhận. Vui lòng tải lại danh sách.");
        if ("MODE_A".equals(b.get("booking_mode")) && !Boolean.TRUE.equals(repository.opportunityDetailExistsBookingAssignments(Boolean.class, id, staff)))
            throw error(HttpStatus.NOT_FOUND, "Lời mời này không dành cho bạn.");
        var profile = ops.identityClient().get("/api/v1/identity/auth/profile", token);
        var match = matchItem(staff, b, profile);
        var eligibility = (Map<String, Object>) match.get("eligibility");
        if (!Boolean.TRUE.equals(eligibility.get("allowed")))
            throw error(HttpStatus.CONFLICT, "Ca hiện không đáp ứng điều kiện nhận việc. Vui lòng tải lại danh sách hoặc kiểm tra lịch làm.");
        var value = new LinkedHashMap<>((Map<String, Object>) match.get("opportunity"));
        StaffJobPresentation.add(value, b, ops.object(b.get("service_snapshot")), ops.object(b.get("address_snapshot")));
        value.put("address", StaffJobPresentation.address(ops.object(b.get("address_snapshot"))));
        value.put("notes", b.get("notes"));
        return value;
    }

    Map<String, Object> assignmentView(long staff, Map<String, Object> b) {
        var assignments = repository.assignmentViewFindBookingAssignments(staff, b.get("id"));
        if (assignments.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Chưa có ca được nhận.");
        var a = assignments.getFirst();
        var service = ops.object(b.get("service_snapshot"));
        var address = ops.object(b.get("address_snapshot"));
        var value = new LinkedHashMap<String, Object>();
        value.put("id", b.get("id"));
        value.put("assignmentId", a.get("id"));
        value.put("bookingCode", b.get("booking_code"));
        value.put("staffId", staff);
        value.put("customerId", b.get("customer_id"));
        value.put("serviceId", b.get("service_id"));
        value.put("serviceName", Objects.toString(service.get("name"), Objects.toString(service.get("serviceName"), "")));
        value.put("customerName", Objects.toString(address.get("recipientName"), Objects.toString(address.get("receiverName"), "")));
        value.put("customerPhone", a.get("accepted_at") == null ? "" : Objects.toString(address.get("recipientPhone"), Objects.toString(address.get("receiverPhone"), "")));
        value.put("customerAvatar", "");
        value.put("address", a.get("accepted_at") == null ? BookingOperationsService.location(address) : StaffJobPresentation.address(address));
        value.put("district", BookingOperationsService.location(address));
        value.put("date", ((Timestamp) b.get("starts_at")).toInstant().atZone(ZONE).toLocalDate().toString());
        value.put("timeSlot", ((Timestamp) b.get("starts_at")).toInstant().atZone(ZONE).toLocalTime() + " - " + ((Timestamp) b.get("ends_at")).toInstant().atZone(ZONE).toLocalTime());
        value.put("startsAt", instant(b.get("starts_at")));
        value.put("endsAt", instant(b.get("ends_at")));
        value.put("packageTitle", Objects.toString(service.get("packageName"), ""));
        value.put("notes", b.get("notes"));
        value.put("totalCustomerPaid", b.get("total_amount"));
        value.put("platformFee", 0);
        value.put("netIncome", 0);
        value.put("incomePendingPolicy", !"CANCELLED".equals(a.get("execution_status")));
        value.put("cancellationReason", a.get("cancellation_reason"));
        value.put("responseTimedOut", "Hết thời gian nhận đơn".equals(a.get("cancellation_reason")));
        value.put("bookingStatus", b.get("status"));
        value.put("status", a.get("execution_status"));
        value.put("checklist", ops.objects(a.get("checklist")));
        value.put("acceptedAt", instant(a.get("accepted_at")));
        value.put("completedAt", instant(b.get("completed_at")));
        var conversation = repository.assignmentViewFindConversations(Long.class, b.get("id"), staff);
        value.put("conversationId", conversation.isEmpty() ? null : conversation.getFirst());
        StaffJobPresentation.add(value, b, service, address);
        return value;
    }

    public List<Map<String, Object>> assignments(String token) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        return repository.assignmentsFindBookings(staff).stream().map(b -> assignmentView(staff, b)).toList();
    }

    @Transactional
    public Map<String, Object> claim(String token, long id) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        ops.lockStaff(staff);
        var b = row(id, true);
        if (Boolean.TRUE.equals(repository.claimExistsBookingAssignments(Boolean.class, id, staff)))
            return assignmentView(staff, b);
        if (!Boolean.TRUE.equals(b.get("pricing_confirmed")) || !List.of("PENDING", "MATCHING", "STAFF_ASSIGNED").contains(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Ca không còn nhận được.");
        var profile = ops.identityClient().get("/api/v1/identity/auth/profile", token);
        var failures = eligibility(staff, b, profile);
        if (!failures.isEmpty())
            throw error(HttpStatus.CONFLICT, failures.stream().map(REASONS::get).reduce((a, c) -> a + " " + c).orElse("Ca không phù hợp."));
        if ("MODE_A".equals(b.get("booking_mode"))) {
            int accepted = repository.claimUpdateBookingAssignments(id, staff);
            if (accepted != 1)
                throw error(HttpStatus.CONFLICT, "Hết thời gian nhận đơn. Vui lòng chờ lời mời mới.");
        } else
            repository.claimInsertBookingAssignments(id, staff);
        var definition = ops.object(b.get("service_snapshot"));
        List<Map<String, Object>> checks = new ArrayList<>();
        Object rawSteps = definition.get("workflow");
        if (rawSteps instanceof List<?> steps) {
            for (int i = 0; i < steps.size(); i++) checks.add(Map.of("id", "step-" + i, "title", Objects.toString(steps.get(i), ""), "done", false));
        }
        repository.claimUpdateBookingAssignments2(json.writeValueAsString(checks), id, staff);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String field : List.of("id", "fullName", "avatar", "rating", "reviewCount", "experienceYears", "isVerified")) if (profile.get(field) != null)
            snapshot.put(field, profile.get(field));
        var score = repository.claimCountReviews(staff);
        snapshot.put("rating", score.get("rating"));
        snapshot.put("reviewCount", score.get("reviews"));
        repository.claimUpdateBookingAssignments3(json.writeValueAsString(snapshot), id, staff);
        repository.claimInsertStaffReservations(staff, id, b.get("starts_at"), Timestamp.from(((Timestamp) b.get("ends_at")).toInstant().plusSeconds(3600)));
        repository.claimInsertConversations(id, b.get("customer_id"), staff);
        repository.claimUpdateBookings(id);
        long filled = repository.claimCountBookingAssignments(Long.class, id);
        if (filled >= ((Number) b.get("required_staff_count")).longValue())
            transition(b, "CONFIRMED", staff, "STAFF", "Đã đủ nhân viên xác nhận ca.");
        else
            transition(b, "MATCHING", staff, "STAFF", "Nhân viên đã nhận một vị trí, tiếp tục tìm nhân viên.");
        return assignmentView(staff, row(id, false));
    }

    void transition(Map<String, Object> b, String next, long user, String role, String reason) {
        repository.transitionUpdateBookings(next, b.get("id"));
        repository.transitionInsertBookingStatusHistories(b.get("id"), b.get("status"), next, "SYSTEM".equals(role) ? null : user, role, reason);
        repository.transitionInsertOutboxEvents(b.get("id"), next, json.writeValueAsString(Map.of("bookingId", b.get("id"), "customerId", b.get("customer_id"), "status", next, "oldStatus", b.get("status"), "reason", reason)));
    }

    @Transactional
    public Map<String, Object> progress(String token, long id, Map<String, Object> body) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        ops.lockStaff(staff);
        var b = row(id, true);
        owned(Map.of("id", staff, "role", "STAFF"), b);
        var rows = repository.progressFindBookingAssignmentsForUpdate(id, staff);
        if (rows.isEmpty())
            throw error(HttpStatus.CONFLICT, "Ca chưa được bạn nhận.");
        var a = rows.getFirst();
        String next = text(body, "status", 20, true), current = (String) a.get("execution_status");
        if (next.equals(current))
            return assignmentView(staff, b);
        if (List.of("CANCELLED", "REFUNDING", "REFUNDED").contains(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Đơn đã kết thúc.");
        var allowed = Map.of("ACCEPTED", List.of("EN_ROUTE", "CANCELLED"), "EN_ROUTE", List.of("IN_PROGRESS", "CANCELLED"), "IN_PROGRESS", List.of("COMPLETED", "CANCELLED"));
        if (!allowed.getOrDefault(current, List.of()).contains(next))
            throw error(HttpStatus.CONFLICT, "Không thể chuyển trạng thái ca từ " + current + " sang " + next + ".");
        if (List.of("EN_ROUTE", "IN_PROGRESS").contains(next) && repository.progressCountBookingAssignments(Long.class, id) < ((Number) b.get("required_staff_count")).longValue())
            throw error(HttpStatus.CONFLICT, "Đơn chưa đủ nhân viên xác nhận. Vui lòng chờ đủ vị trí trước khi thực hiện ca.");
        if (next.equals("CANCELLED") && b.get("started_at") != null)
            throw error(HttpStatus.CONFLICT, "Ca đã bắt đầu thực hiện. Hãy báo cáo sự cố để Admin xử lý thay vì tự hủy và tìm người thay thế.");
        String assignmentState = next.equals("COMPLETED") ? "COMPLETED" : next.equals("CANCELLED") ? "CANCELLED" : "ACCEPTED";
        repository.progressUpdateBookingAssignments(next, assignmentState, a.get("id"));
        if (next.equals("IN_PROGRESS")) {
            repository.progressUpdateBookings(id);
            transition(b, "IN_PROGRESS", staff, "STAFF", "Nhân viên bắt đầu thực hiện dịch vụ.");
            repository.progressUpdateStaffReservations(id, staff);
        }
        if (next.equals("COMPLETED")) {
            repository.progressUpdateStaffReservations2(id, staff);
            long done = repository.progressCountBookingAssignments2(Long.class, id);
            if (done >= ((Number) b.get("required_staff_count")).longValue()) {
                repository.progressUpdateBookings2(id);
                transition(b, "COMPLETED", staff, "STAFF", "Tất cả nhân viên đã hoàn thành dịch vụ.");
                repository.progressUpdateConversations(id);
            }
        }
        if (next.equals("CANCELLED")) {
            String reason = text(body, "reason", 2000, true);
            repository.progressUpdateStaffReservations3(id, staff);
            repository.progressUpdateConversations2(id, staff);
            repository.progressInsertBookingIncidents(id, staff, reason);
            transition(b, "MATCHING", staff, "STAFF", "Nhân viên hủy ca: " + reason);
        }
        return assignmentView(staff, row(id, false));
    }

    @Transactional
    public Map<String, Object> checklist(String token, long id, Map<String, Object> body) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        var b = row(id, true);
        owned(Map.of("id", staff, "role", "STAFF"), b);
        if (List.of("COMPLETED", "CANCELLED", "REFUNDED").contains(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Ca đã kết thúc.");
        var rows = repository.checklistFindBookingAssignments(id, staff);
        if (rows.isEmpty())
            throw error(HttpStatus.FORBIDDEN, "Bạn chưa nhận ca này.");
        var checks = ops.objects(rows.getFirst().get("checklist"));
        String key = text(body, "id", 100, true);
        boolean found = false;
        if (body.containsKey("done") && !(body.get("done") instanceof Boolean))
            throw error(HttpStatus.BAD_REQUEST, "Trạng thái checklist không hợp lệ.");
        for (var check : checks) if (key.equals(Objects.toString(check.get("id")))) {
            check.put("done", body.containsKey("done") ? body.get("done") : !Boolean.TRUE.equals(check.get("done")));
            found = true;
        }
        if (!found)
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy hạng mục.");
        repository.checklistUpdateBookingAssignments(json.writeValueAsString(checks), rows.getFirst().get("id"));
        return assignmentView(staff, b);
    }

    @Transactional
    public Map<String, Object> cancel(String token, long id, Map<String, Object> body) {
        var actor = ops.actor(token, "CUSTOMER", "ADMIN");
        var b = row(id, true);
        owned(actor, b);
        if ("CANCELLED".equals(b.get("status")))
            return bookingView(b);
        if (b.get("started_at") != null || !List.of("PENDING", "MATCHING", "CONFIRMED", "ACCEPTED", "STAFF_ASSIGNED").contains(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Đơn không còn được hủy ở trạng thái này.");
        String reason = text(body, "reason", 2000, true);
        repository.cancelUpdateBookings(reason, actor.get("id"), actor.get("role"), id);
        repository.cancelUpdateBookings2(id);
        repository.cancelUpdateBookingAssignments(reason, id);
        repository.cancelUpdateStaffReservations(id);
        repository.cancelUpdateConversations(id);
        transition(b, "CANCELLED", ServiceHttpClient.userId(actor), (String) actor.get("role"), reason);
        return bookingView(row(id, false));
    }

    public List<Map<String, Object>> reviews(String token) {
        var user = ops.actor(token, "STAFF", "ADMIN", "CUSTOMER");
        return repository.reviewsFindReviews(user).stream().map(r -> {
            r.put("tags", json.readValue(Objects.toString(r.get("tags"), "[]"), new TypeReference<List<String>>() {
            }));
            if (!"ADMIN".equals(user.get("role")))
                r.remove("internalNote");
            return r;
        }).toList();
    }

    @Transactional
    public Map<String, Object> moderateReview(String token, long id, Map<String, Object> b) {
        ops.actor(token, "ADMIN");
        String status = text(b, "status", 20, true);
        if (!List.of("PUBLISHED", "HIDDEN", "RESOLVED").contains(status))
            throw error(HttpStatus.BAD_REQUEST, "Trạng thái kiểm duyệt không hợp lệ.");
        if (repository.moderateReviewUpdateReviews(status, text(b, "internalNote", 2000, false), id) == 0)
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá.");
        return Map.of("id", id, "status", status);
    }

    @Transactional
    public Map<String, Object> incident(String token, long id, Map<String, Object> body) {
        var user = ops.actor(token, "STAFF", "CUSTOMER", "ADMIN");
        var b = row(id, false);
        owned(user, b);
        long issue = repository.incidentInsertBookingIncidents(id, user.get("id"), user.get("role"), text(body, "reason", 2000, true));
        return Map.of("id", issue, "status", "OPEN");
    }

    @Transactional
    public Map<String, Object> reject(String token, long id) {
        long staff = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        ops.lockStaff(staff);
        var b = row(id, true);
        int count = repository.rejectUpdateBookingAssignments(id, staff);
        if (count == 0)
            throw error(HttpStatus.CONFLICT, "Không có lời mời đang chờ cho tài khoản của bạn.");
        transition(b, "NO_STAFF_FOUND", staff, "STAFF", "Nhân viên từ chối lời mời.");
        return Map.of("id", id, "status", "REJECTED");
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> createReview(String token, long id, Map<String, Object> body) {
        var user = ops.actor(token, "CUSTOMER");
        var b = row(id, true);
        owned(user, b);
        if (!"COMPLETED".equals(b.get("status")))
            throw error(HttpStatus.CONFLICT, "Chỉ được đánh giá sau khi đơn hoàn thành.");
        long staff = num(body, "staffId", 0, 1, Long.MAX_VALUE), rating = num(body, "rating", 0, 1, 5);
        if (!Boolean.TRUE.equals(repository.createReviewExistsBookingAssignments(Boolean.class, id, staff)))
            throw error(HttpStatus.BAD_REQUEST, "Nhân viên không thuộc đơn đã hoàn thành.");
        String comment = text(body, "comment", 2000, false);
        var normalizedTags = stringList(body.getOrDefault("tags", List.of()));
        var prior = repository.createReviewFindReviews(id, staff);
        if (!prior.isEmpty()) {
            var old = prior.getFirst();
            if (((Number) old.get("rating")).longValue() != rating || !Objects.toString(old.get("comment"), "").equals(comment) || !json.readValue(old.get("tags").toString(), new TypeReference<List<String>>() {
            }).equals(normalizedTags))
                throw error(HttpStatus.CONFLICT, "Đơn đã được đánh giá.");
            return Map.of("id", old.get("id"));
        }
        var tags = normalizedTags;
        long review = repository.createReviewInsertReviews(id, user.get("id"), staff, b.get("service_id"), rating, comment, json.writeValueAsString(tags));
        return Map.of("id", review);
    }

    public List<Map<String, Object>> publicReviews(long serviceId) {
        return repository.publicReviewsFindReviews(serviceId);
    }

    public List<Map<String, Object>> publicReviewStats() {
        return repository.publicReviewStatsCountReviews();
    }
}
