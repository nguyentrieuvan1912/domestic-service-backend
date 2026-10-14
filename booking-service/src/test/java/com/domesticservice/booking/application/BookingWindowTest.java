package com.domesticservice.booking.application;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class BookingWindowTest {
    private static Instant at(String time){return OffsetDateTime.parse("2026-10-15T"+time+"+07:00").toInstant();}
    @Test void acceptsOperatingBoundaries(){assertDoesNotThrow(()->BookingOperationsService.validateWindow(at("08:00:00"),at("20:00:00"),false));}
    @Test void rejectsBeforeOpening(){assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("07:30:00"),at("09:00:00"),false));}
    @Test void rejectsAfterClosing(){assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("19:30:00"),at("20:30:00"),false));}
    @Test void rejectsFifteenMinuteStep(){assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("09:15:00"),at("10:30:00"),false));}
    @Test void rejectsFractionalSeconds(){assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("09:00:00.001"),at("10:00:00"),false));}
    @Test void rejectsZeroAndReversedDuration(){assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("09:00:00"),at("09:00:00"),false));assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(at("10:00:00"),at("09:00:00"),false));}
    @Test void rejectsPastAndOutsideSevenDayHorizon(){var now=LocalDate.now(BookingOperationsService.ZONE);var past=now.minusDays(1).atTime(9,0).atZone(BookingOperationsService.ZONE).toInstant();var distant=now.plusDays(7).atTime(9,0).atZone(BookingOperationsService.ZONE).toInstant();assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(past,past.plusSeconds(3600),true));assertThrows(ResponseStatusException.class,()->BookingOperationsService.validateWindow(distant,distant.plusSeconds(3600),true));}
    @Test void normalizesCityNamesWithoutMixingCities(){assertEquals("ho chi minh",BookingOperationsService.city("TP. Hồ Chí Minh"));assertEquals("ho chi minh",BookingOperationsService.city("Thành phố Hồ Chí Minh"));assertNotEquals(BookingOperationsService.city("TP. Hồ Chí Minh"),BookingOperationsService.city("Hà Nội"));}
    @Test void districtNormalizationKeepsNumericDistricts(){assertEquals("Quận 1",BookingOperationsService.district(" Quận 1 "));assertEquals("Bình Thạnh",BookingOperationsService.district("Quận Bình Thạnh"));}
}
