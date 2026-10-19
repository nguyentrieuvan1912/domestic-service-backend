package com.domesticservice.booking.repository;

import com.domesticservice.booking.entity.StaffScheduleWeek;
import com.domesticservice.booking.entity.StaffScheduleWeekId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleWeekRepository extends JpaRepository<StaffScheduleWeek, StaffScheduleWeekId> {
}
