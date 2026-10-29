package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.domesticservice.booking.service.BookingOperationsService.*;
import com.domesticservice.booking.repository.StaffResponseSqlRepository;

/**
 * A response window is server-owned; expiry and acceptance serialize on the booking row.
 */
@Service
public class StaffResponseService {

    static final String TIMEOUT_REASON = "Hết thời gian nhận đơn";

    private final StaffResponseSqlRepository repository;

    private final BookingOperationsService ops;

    private final BookingWorkflowService flow;

    private final TransactionTemplate tx;

    public StaffResponseService(StaffResponseSqlRepository repository, BookingOperationsService ops, BookingWorkflowService flow, PlatformTransactionManager manager) {
        this.repository = repository;
        this.ops = ops;
        this.flow = flow;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 10000)
    public void expireInvitations() {
        // Small bounded batches; each booking commits separately and survives application restarts.
        var ids = repository.expireInvitationsFindBookings(Long.class);
        for (long id : ids) tx.executeWithoutResult(status -> {
            var rows = repository.expireInvitationsFindBookingsForUpdate(id);
            if (!rows.isEmpty())
                expire(rows.getFirst());
        });
        repository.expireInvitationsUpdateBookings();
    }

    private void expire(Map<String, Object> b) {
        if (!"WAITING".equals(b.get("staff_wait_state")) || !"STAFF_ASSIGNED".equals(b.get("status")) || ((Timestamp) b.get("staff_response_deadline")).toInstant().isAfter(Instant.now()))
            return;
        long id = ((Number) b.get("id")).longValue();
        repository.expireUpdateBookingAssignments(TIMEOUT_REASON, id);
        repository.expireUpdateStaffReservations(id);
        repository.expireUpdateBookings(TIMEOUT_REASON, id);
        flow.transition(b, "CANCELLED", ((Number) b.get("customer_id")).longValue(), "SYSTEM", TIMEOUT_REASON);
    }

    public Map<String, Object> decide(String token, long id, Map<String, Object> body) {
        var actor = ops.actor(token, "CUSTOMER");
        long customer = ServiceHttpClient.userId(actor);
        String action = text(body, "action", 10, true);
        if (!List.of("EXTEND", "CANCEL", "STOP").contains(action))
            throw error(HttpStatus.BAD_REQUEST, "Lựa chọn chờ không hợp lệ.");
        long round = num(body, "round", 0, 1, Integer.MAX_VALUE);
        return tx.execute(status -> {
            // Same lock ordering as claim/create prevents competing bookings from reserving this staff.
            var invited = repository.decideFindBookingAssignments(id, customer);
            if (invited.isEmpty())
                throw error(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời thuộc tài khoản của bạn.");
            var assignment = invited.getFirst();
            long staff = ((Number) assignment.get("staff_id")).longValue();
            ops.lockStaff(staff);
            var b = repository.decideFindBookingsForUpdate(id, customer);
            expire(b);
            b = repository.decideFindBookings(id);
            int current = ((Number) b.get("staff_wait_round")).intValue();
            if ("EXTEND".equals(action) && current == round + 1 && "WAITING".equals(b.get("staff_wait_state")))
                return flow.bookingView(b);
            if (!"EXTEND".equals(action) && current == round && "CLOSED".equals(b.get("staff_wait_state")) && "CANCELLED".equals(b.get("status")))
                return flow.bookingView(b);
            if (current != round)
                throw error(HttpStatus.CONFLICT, "Thời gian chờ đã được cập nhật. Vui lòng tải lại đơn.");
            if (!"EXPIRED".equals(b.get("staff_wait_state")) || !"CANCELLED".equals(b.get("status")))
                throw error(HttpStatus.CONFLICT, "Đơn chưa hết thời gian chờ hoặc đã được nhân viên nhận.");
            if (!"EXTEND".equals(action)) {
                String reason = TIMEOUT_REASON + ("STOP".equals(action) ? ". Khách hàng hủy đặt dịch vụ, không tiếp tục đặt." : ". Khách hàng chọn tìm nhân viên khác.");
                repository.decideUpdateBookings(reason, customer, id);
                flow.transition(b, "CANCELLED", customer, "CUSTOMER", reason);
            } else {
                var profile = ops.identityClient().list("/api/v1/identity/staff-directory", token).stream().filter(p -> ServiceHttpClient.userId(p) == staff).findFirst().orElse(null);
                var candidate = new LinkedHashMap<>(b);
                candidate.put("booking_mode", "MODE_B");
                if (!flow.eligibility(staff, candidate, profile).isEmpty())
                    throw error(HttpStatus.CONFLICT, "Nhân viên hiện không còn phù hợp khung giờ này. Vui lòng tìm nhân viên khác.");
                for (var related : maps(ops.object(b.get("service_snapshot")).getOrDefault("relatedServices", List.of()))) {
                    var extra = new LinkedHashMap<>(candidate);
                    extra.put("service_id", related.get("id"));
                    if (!flow.eligibility(staff, extra, profile).isEmpty())
                        throw error(HttpStatus.CONFLICT, "Nhân viên không còn đủ điều kiện thực hiện dịch vụ bổ sung. Vui lòng tìm nhân viên khác.");
                }
                if (((Timestamp) b.get("starts_at")).toInstant().isBefore(Instant.now().plusSeconds(300)))
                    throw error(HttpStatus.CONFLICT, "Không còn đủ 5 phút trước giờ làm. Vui lòng đặt lại thời gian khác.");
                repository.decideInsertBookingAssignments(id, staff, Objects.toString(assignment.get("staff_snapshot"), "{}"));
                repository.decideUpdateBookings2(id);
                flow.transition(b, "STAFF_ASSIGNED", customer, "CUSTOMER", "Khách hàng xác nhận chờ thêm 5 phút.");
            }
            return flow.bookingView(repository.decideFindBookings2(id));
        });
    }
}
