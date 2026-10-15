package com.domesticservice.booking.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulePriorityTest {
    @Test void unregisteredWeekStopsBeforeOtherEligibilityChecks() {
        var db=mock(JdbcTemplate.class);
        var ops=new BookingOperationsService(db,new ObjectMapper(),"http://localhost:1","http://localhost:1");
        var flow=new BookingWorkflowService(ops,db,new ObjectMapper());
        var result=flow.eligibility(99L,Map.of("starts_at",Timestamp.from(Instant.parse("2026-10-16T02:00:00Z"))),null);
        assertEquals(List.of("SCHEDULE_REQUIRED"),result);
        verify(db).queryForObject(anyString(),eq(Boolean.class),eq(99L),eq(java.time.LocalDate.parse("2026-10-12")));
        verifyNoMoreInteractions(db);
    }
}
