package com.domesticservice.booking.service;

import java.time.*;
import org.springframework.http.HttpStatus;
import static com.domesticservice.booking.service.BookingOperationsService.*;

final class CustomerBookingTimePolicy {

    static void validate(Instant start, Instant now) {
        if (start.isBefore(now.plus(Duration.ofHours(2))))
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng đặt dịch vụ trước giờ bắt đầu tối thiểu 2 giờ để nhân viên có thời gian xác nhận và di chuyển.");
    }
}
