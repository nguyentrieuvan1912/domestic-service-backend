package com.domesticservice.booking.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WeeklySchedulePolicyTest {
    private Instant at(String time) { return OffsetDateTime.parse("2026-10-15T"+time+":00+07:00").toInstant(); }
    private WeeklySchedulePolicy.Period p(String start,String end) { return new WeeklySchedulePolicy.Period(at(start),at(end)); }
    @Test void weekUsesVietnamMondayEvenAcrossUtcMidnight() {
        assertEquals(LocalDate.parse("2026-10-12"),WeeklySchedulePolicy.weekStart(Instant.parse("2026-10-11T17:01:00Z")));
        assertEquals(LocalDate.parse("2026-10-12"),WeeklySchedulePolicy.weekStart(Instant.parse("2026-10-18T16:59:00Z")));
    }
    @Test void pastCannotBeRemovedOrAdded() {
        assertThrows(ResponseStatusException.class,()->WeeklySchedulePolicy.protectPast(List.of(p("08:00","12:00")),List.of(),at("10:15")));
        assertThrows(ResponseStatusException.class,()->WeeklySchedulePolicy.protectPast(List.of(),List.of(p("08:00","12:00")),at("10:15")));
    }
    @Test void ongoingPeriodCanChangeOnlyItsFuture() {
        assertDoesNotThrow(()->WeeklySchedulePolicy.protectPast(List.of(p("08:00","12:00")),List.of(p("08:00","10:30")),at("10:15")));
        assertThrows(ResponseStatusException.class,()->WeeklySchedulePolicy.protectPast(List.of(p("08:00","12:00")),List.of(p("08:00","10:00")),at("10:15")));
    }
    @Test void intervalRepresentationDoesNotChangeHistoricalCoverage() {
        assertDoesNotThrow(()->WeeklySchedulePolicy.protectPast(List.of(p("08:00","12:00")),List.of(p("08:00","09:00"),p("09:00","12:00")),at("13:00")));
        assertFalse(WeeklySchedulePolicy.covers(List.of(p("08:00","09:00"),p("09:30","12:00")),at("08:00"),at("12:00")));
    }
    @Test void futureCanBeReplacedAndAcceptedWorkMustBeFullyCovered() {
        assertDoesNotThrow(()->WeeklySchedulePolicy.protectPast(List.of(p("12:00","17:00")),List.of(p("14:00","20:00")),at("10:15")));
        assertTrue(WeeklySchedulePolicy.covers(List.of(p("08:00","10:00"),p("10:00","12:00")),at("09:00"),at("11:00")));
        assertFalse(WeeklySchedulePolicy.covers(List.of(p("08:00","10:00")),at("09:00"),at("11:00")));
    }
}
