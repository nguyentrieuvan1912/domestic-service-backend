package com.domesticservice.booking.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class FavouriteStaffDurationPolicyTest {
    private Map<String,Object> extras(int minutes){return Map.of("addOns",List.of(Map.of("durationMinutes",minutes)));}
    @Test void acceptsFourHoursWithoutExtras(){assertDoesNotThrow(()->FavouriteStaffDurationPolicy.validate("MODE_A",240,Map.of()));}
    @Test void acceptsLowerPackageWithAddonAtExactLimit(){assertDoesNotThrow(()->FavouriteStaffDurationPolicy.validate("MODE_A",240,extras(30)));}
    @Test void rejectsFourHoursWithZeroDurationAddon(){assertThrows(ResponseStatusException.class,()->FavouriteStaffDurationPolicy.validate("MODE_A",240,extras(0)));}
    @Test void rejectsTotalAboveFourHours(){assertThrows(ResponseStatusException.class,()->FavouriteStaffDurationPolicy.validate("MODE_A",270,extras(60)));}
    @Test void countsRelatedServices(){assertThrows(ResponseStatusException.class,()->FavouriteStaffDurationPolicy.validate("MODE_A",300,Map.of("relatedServices",List.of(Map.of("durationMinutes",60)))));}
    @Test void keepsModeBPolicy(){assertDoesNotThrow(()->FavouriteStaffDurationPolicy.validate("MODE_B",480,extras(60)));}
}
