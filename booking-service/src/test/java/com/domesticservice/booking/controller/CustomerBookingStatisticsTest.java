package com.domesticservice.booking.controller;

import com.domesticservice.booking.repository.BookingWorkflowSqlRepository;
import com.domesticservice.booking.service.BookingOperationsService;
import com.domesticservice.booking.service.BookingWorkflowService;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:customer_booking_statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class CustomerBookingStatisticsTest {
    @Autowired JdbcTemplate database;
    @Autowired NativeQueryExecutor queries;
    @Autowired ObjectMapper json;

    @Test void countsAllCompletedBookingsForTheAuthenticatedCustomer() {
        database.execute("CREATE TABLE bookings(customer_id BIGINT,status VARCHAR(30))");
        database.update("INSERT INTO bookings SELECT 11,'COMPLETED' FROM SYSTEM_RANGE(1,601)");
        database.update("INSERT INTO bookings VALUES (11,'IN_PROGRESS'),(11,'CANCELLED'),(12,'COMPLETED')");
        var ops = mock(BookingOperationsService.class);
        when(ops.actor("customer", "CUSTOMER")).thenReturn(Map.of("id",11L,"role","CUSTOMER"));
        when(ops.actor("other", "CUSTOMER")).thenReturn(Map.of("id",12L,"role","CUSTOMER"));
        when(ops.actor("empty", "CUSTOMER")).thenReturn(Map.of("id",13L,"role","CUSTOMER"));
        when(ops.actor(null, "CUSTOMER")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(ops.actor("staff", "CUSTOMER")).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        var service = new BookingWorkflowService(ops, new BookingWorkflowSqlRepository(queries), json);
        var controller = new BookingOperationsController(ops, service, null);
        assertEquals(601L, controller.customerStatistics("customer").get("completedBookings"));
        assertEquals(1L, controller.customerStatistics("other").get("completedBookings"));
        assertEquals(0L, controller.customerStatistics("empty").get("completedBookings"));
        assertThrows(ResponseStatusException.class, () -> controller.customerStatistics(null));
        assertThrows(ResponseStatusException.class, () -> controller.customerStatistics("staff"));
    }
}
