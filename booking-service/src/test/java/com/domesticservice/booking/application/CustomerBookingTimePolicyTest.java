package com.domesticservice.booking.application;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CustomerBookingTimePolicyTest {
    private final Instant now=Instant.parse("2026-10-15T03:20:30Z");
    @Test void acceptsExactlyTwoHoursAndLater() {
        assertDoesNotThrow(()->CustomerBookingTimePolicy.validate(now.plusSeconds(7200),now));
        assertDoesNotThrow(()->CustomerBookingTimePolicy.validate(now.plusSeconds(9000),now));
    }
    @Test void rejectsJustBelowTwoHoursAndPast() {
        assertThrows(ResponseStatusException.class,()->CustomerBookingTimePolicy.validate(now.plusSeconds(7199),now));
        assertThrows(ResponseStatusException.class,()->CustomerBookingTimePolicy.validate(now.minusSeconds(1),now));
    }
    @Test void savingLaterMustRecheckTheSameStart() {
        Instant selected=now.plusSeconds(7200);
        assertThrows(ResponseStatusException.class,()->CustomerBookingTimePolicy.validate(selected,now.plusSeconds(1)));
    }
}
