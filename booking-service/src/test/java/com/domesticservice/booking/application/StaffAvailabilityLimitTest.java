package com.domesticservice.booking.application;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffAvailabilityLimitTest {
    @Test void usesSavedEndAndReservesTravelTimeBeforeNextBooking() {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:availability_limit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE staff_availabilities(staff_id BIGINT,status VARCHAR(30),available_date DATE,day_of_week INT,start_time TIME,end_time TIME)");
        db.execute("CREATE TABLE bookings(id BIGINT,starts_at TIMESTAMP,status VARCHAR(30))");
        db.execute("CREATE TABLE booking_assignments(booking_id BIGINT,staff_id BIGINT,status VARCHAR(30))");
        db.update("INSERT INTO staff_availabilities VALUES (99,'AVAILABLE','2026-10-16',NULL,'08:00','17:00')");
        var service=new BookingCreationService(mock(BookingOperationsService.class),mock(BookingWorkflowService.class),db,new ObjectMapper(),mock(PlatformTransactionManager.class));
        var start=Instant.parse("2026-10-16T02:00:00Z");
        var initial=service.availabilityLimit(99,start);
        assertEquals("2026-10-16T10:00:00Z",initial.get("availableUntil"));
        assertEquals("SCHEDULE_END",initial.get("availableUntilReason"));
        db.update("INSERT INTO bookings VALUES (1,?,'STAFF_ASSIGNED')",Timestamp.from(Instant.parse("2026-10-16T09:00:00Z")));
        db.update("INSERT INTO booking_assignments VALUES (1,99,'ASSIGNED')");
        var reserved=service.availabilityLimit(99,start);
        assertEquals("2026-10-16T08:00:00Z",reserved.get("availableUntil"));
        assertEquals("NEXT_BOOKING",reserved.get("availableUntilReason"));
        db.update("UPDATE bookings SET status='CANCELLED'");
        assertEquals(initial,service.availabilityLimit(99,start));
    }
}
