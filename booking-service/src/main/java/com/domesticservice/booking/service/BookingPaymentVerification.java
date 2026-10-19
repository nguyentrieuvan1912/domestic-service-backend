package com.domesticservice.booking.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.domesticservice.booking.repository.BookingPaymentVerificationSqlRepository;

@Service
public class BookingPaymentVerification {

    private final BookingOperationsService ops;

    private final BookingPaymentVerificationSqlRepository repository;

    private final ServiceHttpClient finance;

    public BookingPaymentVerification(BookingOperationsService ops, BookingPaymentVerificationSqlRepository repository, @Value("${clients.finance.base-url}") String url) {
        this.ops = ops;
        this.repository = repository;
        finance = new ServiceHttpClient(url);
    }

    @Transactional
    public Map<String, Object> verify(String token, long id) {
        ops.actor(token, "ADMIN");
        var proof = finance.get("/api/v1/finance/bookings/" + id + "/payment-status", token);
        var rows = repository.verifyFindBookingsForUpdate(id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn.");
        long total = ((Number) rows.getFirst().get("total_amount")).longValue(), paid = ((Number) proof.get("paidAmount")).longValue(), refunded = ((Number) proof.get("refundedAmount")).longValue();
        String state = paid > 0 && refunded >= paid ? "REFUNDED" : paid - refunded >= total ? "PAID" : "PENDING";
        repository.verifyUpdateBookings(state, id);
        return Map.of("id", id, "paymentStatus", state);
    }
}
