package com.domesticservice.booking.controller;

import com.domesticservice.booking.service.*;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/booking")
public class BookingOperationsController {

    private final BookingOperationsService ops;

    private final BookingWorkflowService flow;

    private final StaffResponseService responses;

    public BookingOperationsController(BookingOperationsService ops, BookingWorkflowService flow, StaffResponseService responses) {
        this.ops = ops;
        this.flow = flow;
        this.responses = responses;
    }

    @GetMapping("/staff/me/availability")
    public Map<String, Object> schedule(@RequestHeader(value = "Authorization", required = false) String token) {
        return ops.schedule(ServiceHttpClient.userId(ops.actor(token, "STAFF")));
    }

    @PutMapping("/staff/me/availability")
    public Map<String, Object> saveSchedule(@RequestHeader(value = "Authorization", required = false) String token, @RequestBody Map<String, Object> body) {
        return ops.saveSchedule(token, body);
    }

    @GetMapping("/staff/me/capabilities")
    public Map<String, Object> capabilities(@RequestHeader(value = "Authorization", required = false) String token) {
        long id = ServiceHttpClient.userId(ops.actor(token, "STAFF"));
        return Map.of("capabilities", ops.capabilities(token, id, false), "restrictions", ops.restrictions(id));
    }

    @GetMapping("/admin/staff/{id}/operations")
    public Map<String, Object> staff(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestParam(defaultValue = "ALL") String period) {
        return ops.staffPeriodOperations(token, id, period);
    }

    @PutMapping("/admin/staff/{id}/operations")
    public Map<String, Object> saveStaff(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return ops.saveStaffOperations(token, id, body);
    }

    @GetMapping("/staff/me/assignments")
    public List<Map<String, Object>> assignments(@RequestHeader(value = "Authorization", required = false) String token) {
        return flow.assignments(token);
    }

    @GetMapping("/staff/me/matching")
    public Map<String, Object> matching(@RequestHeader(value = "Authorization", required = false) String token) {
        return flow.matching(token);
    }

    @GetMapping("/staff/opportunities/{id}")
    public Map<String, Object> opportunity(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return flow.opportunityDetail(token, id);
    }

    @PostMapping("/staff/opportunities/{id}/claim")
    public Map<String, Object> claim(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return flow.claim(token, id);
    }

    @PostMapping("/staff/opportunities/{id}/reject")
    public Map<String, Object> reject(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return flow.reject(token, id);
    }

    @PatchMapping("/staff/assignments/{id}/status")
    public Map<String, Object> status(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return flow.progress(token, id, body);
    }

    @PatchMapping("/staff/assignments/{id}/checklist")
    public Map<String, Object> checklist(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return flow.checklist(token, id, body);
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return flow.list(token);
    }

    @GetMapping("/bookings/{id}")
    public Map<String, Object> detail(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return flow.detail(token, id);
    }

    @GetMapping("/bookings/{id}/history")
    public List<Map<String, Object>> history(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return flow.history(token, id);
    }

    @PostMapping("/bookings/{id}/staff-response")
    public Map<String, Object> response(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return responses.decide(token, id, body);
    }

    @PostMapping("/bookings/{id}/cancel")
    public Map<String, Object> cancel(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return flow.cancel(token, id, body);
    }

    @PostMapping("/bookings/{id}/incidents")
    public Map<String, Object> incident(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return flow.incident(token, id, body);
    }

    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestHeader(value = "Authorization", required = false) String token) {
        return flow.reviews(token);
    }

    @PatchMapping("/admin/reviews/{id}")
    public Map<String, Object> review(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return flow.moderateReview(token, id, body);
    }

    @PostMapping("/bookings/{id}/reviews")
    public Map<String, Object> createReview(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return flow.createReview(token, id, b);
    }

    @GetMapping("/public/services/{id}/reviews")
    public List<Map<String, Object>> publicReviews(@PathVariable long id) {
        return flow.publicReviews(id);
    }

    @GetMapping("/public/review-stats")
    public List<Map<String, Object>> stats() {
        return flow.publicReviewStats();
    }
}
