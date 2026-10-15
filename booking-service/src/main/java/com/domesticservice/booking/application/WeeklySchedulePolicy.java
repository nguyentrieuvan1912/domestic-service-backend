package com.domesticservice.booking.application;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.domesticservice.booking.application.BookingOperationsService.*;

final class WeeklySchedulePolicy {
    record Period(Instant start, Instant end) {}
    static LocalDate weekStart(Instant now) {
        return now.atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    static boolean covers(List<Period> periods, Instant start, Instant end) {
        var sorted = periods.stream().sorted(Comparator.comparing(Period::start)).toList();
        Instant cursor = start;
        for (var period : sorted) {
            if (period.end().isBefore(cursor)) continue;
            if (period.start().isAfter(cursor)) break;
            if (period.end().isAfter(cursor)) cursor = period.end();
            if (!cursor.isBefore(end)) return true;
        }
        return false;
    }
    // Compare the actual union before server time, including the slot already in progress.
    static void protectPast(List<Period> before, List<Period> after, Instant now) {
        for (var p : before) if (p.start().isBefore(now) && !covers(after, p.start(), p.end().isBefore(now) ? p.end() : now))
            throw error(HttpStatus.CONFLICT, "Không thể thay đổi thời gian đã qua hoặc khung giờ đang bắt đầu. Vui lòng tải lại lịch.");
        for (var p : after) if (p.start().isBefore(now) && !covers(before, p.start(), p.end().isBefore(now) ? p.end() : now))
            throw error(HttpStatus.CONFLICT, "Không thể đăng ký thêm thời gian đã qua. Vui lòng tải lại lịch.");
    }
}
