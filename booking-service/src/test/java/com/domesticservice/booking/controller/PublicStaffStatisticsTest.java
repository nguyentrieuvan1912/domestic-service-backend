package com.domesticservice.booking.controller;

import com.domesticservice.booking.application.BookingOperationsService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicStaffStatisticsTest {
    @Test void defaultsToFiveWithoutInventingReviewsAndUsesPublishedAverage() {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:staff_rating;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE booking_assignments(staff_id BIGINT,status VARCHAR(30))");
        db.execute("CREATE TABLE reviews(staff_id BIGINT,rating INT,moderation_status VARCHAR(30))");
        db.execute("CREATE TABLE staff_service_capabilities(staff_id BIGINT,service_id BIGINT,status VARCHAR(30),valid_from TIMESTAMP,valid_until TIMESTAMP)");
        db.update("INSERT INTO staff_service_capabilities VALUES (1,2,'APPROVED',NULL,NULL),(2,2,'APPROVED',NULL,NULL)");
        db.update("INSERT INTO reviews VALUES (2,2,'PUBLISHED'),(2,4,'RESOLVED'),(2,5,'HIDDEN')");
        var ops = mock(BookingOperationsService.class);
        when(ops.services(null)).thenReturn(Map.of());
        var stats = new PublicStaffStatisticsController(db, ops).stats();
        var unrated = stats.stream().filter(item -> ((Number)item.get("staffId")).longValue() == 1).findFirst().orElseThrow();
        assertEquals(5, ((Number)unrated.get("rating")).doubleValue());
        assertEquals(0, ((Number)unrated.get("reviewCount")).longValue());
        var rated = stats.stream().filter(item -> ((Number)item.get("staffId")).longValue() == 2).findFirst().orElseThrow();
        assertEquals(3, ((Number)rated.get("rating")).doubleValue());
        assertEquals(2, ((Number)rated.get("reviewCount")).longValue());
        assertEquals(3, db.queryForObject("SELECT count(*) FROM reviews", Integer.class));
    }
}
