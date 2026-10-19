package com.domesticservice.booking.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ServiceAreaPolicyTests {
    @Test void onlyHcmAddressesCanBeBookedOrUsedForStaffSearch() {
        for (String city : new String[]{"TP. Hồ Chí Minh", "Thành phố Hồ Chí Minh", "Hồ Chí Minh", "HCM", "TP.HCM"})
            assertThatCode(() -> BookingCreationService.requireSupportedAddress(Map.of("city", city))).doesNotThrowAnyException();
        assertThatCode(() -> BookingCreationService.requireSupportedAddress(Map.of("province", "TP. Hồ Chí Minh"))).doesNotThrowAnyException();
        for (String city : new String[]{"Hà Nội", "HN", "Đà Nẵng", "", "TP. Hồ Chí Minh Hà Nội"})
            assertThatThrownBy(() -> BookingCreationService.requireSupportedAddress(Map.of("city", city)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> BookingCreationService.requireSupportedAddress(Map.of())).isInstanceOf(ResponseStatusException.class);
    }
}
