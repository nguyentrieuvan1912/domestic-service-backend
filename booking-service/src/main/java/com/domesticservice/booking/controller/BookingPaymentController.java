package com.domesticservice.booking.controller;

import com.domesticservice.booking.service.BookingPaymentVerification;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/booking/admin/bookings")
public class BookingPaymentController {

    private final BookingPaymentVerification service;

    public BookingPaymentController(BookingPaymentVerification service) {
        this.service = service;
    }

    @PostMapping("/{id}/verify-payment")
    public Map<String, Object> verify(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return service.verify(token, id);
    }
}
