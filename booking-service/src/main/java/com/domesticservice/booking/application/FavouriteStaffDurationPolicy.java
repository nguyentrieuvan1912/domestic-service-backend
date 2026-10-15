package com.domesticservice.booking.application;

import java.util.*;
import org.springframework.http.HttpStatus;
import static com.domesticservice.booking.application.BookingOperationsService.*;

final class FavouriteStaffDurationPolicy {
    static void validate(String mode,int totalMinutes,Map<String,Object> snapshot) {
        if(!"MODE_A".equals(mode))return;
        var extras=new ArrayList<>(maps(snapshot.getOrDefault("addOns",List.of())));
        extras.addAll(maps(snapshot.getOrDefault("relatedServices",List.of())));
        int extraMinutes=extras.stream().mapToInt(e->((Number)e.get("durationMinutes")).intValue()).sum();
        if(totalMinutes>240 || totalMinutes-extraMinutes>=240&&!extras.isEmpty())
            throw error(HttpStatus.BAD_REQUEST,"Nhân viên yêu thích chỉ làm tối đa 4 giờ cho một đơn, gồm gói chính và dịch vụ bổ sung. Gói 4 giờ không thể thêm addon; vui lòng giữ gói 4 giờ hoặc hạ gói để chọn thêm addon.");
    }
}
