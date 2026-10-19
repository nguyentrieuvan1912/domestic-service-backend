package com.domesticservice.catalog.service;

import com.domesticservice.catalog.dto.CatalogResponse.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PackageRecommendationServiceTests {
    final ObjectMapper json = new ObjectMapper();
    ServicePackage pack(long id, long price, int minutes, int staff, Integer area) {
        return new ServicePackage(id, "Gói " + id, "", minutes, price, staff, area == null ? null : BigDecimal.valueOf(area));
    }
    Detail detail(String unit, ServicePackage... packages) {
        var service = new ServiceSummary(1,1,"CLEANING","Vệ sinh","REAL","Dọn nhà",null,null,"CLEANING_HOURLY",unit,140000,120,false,null,List.of());
        var field = new Requirement(1,"areaM2","Diện tích","NUMBER",true,json.readTree("[]"),json.readTree("{\"min\":10,\"max\":2000,\"integer\":true}"),1);
        return new Detail(service,List.of(packages),List.of(),List.of(field),List.of(),List.of());
    }
    PackageRecommendation plan(Detail d, int area, int staff, int minutes) { return PackageRecommendationService.plan(d,BigDecimal.valueOf(area),staff,minutes); }
    @Test void picksCheapestSufficientPackageAndTeamInsteadOfCheapestStickerPrice() {
        var d=detail("PACKAGE",pack(1,140000,120,1,55),pack(2,195000,180,1,85),pack(3,260000,240,1,120));
        assertEquals(2,plan(d,80,20,720).options().getFirst().packageId());
        var large=plan(d,130,20,720).options().getFirst();
        assertEquals(2,large.packageId()); assertEquals(2,large.requiredStaffCount()); assertEquals(390000,large.baseAmount());
        assertEquals(3,plan(d,110,20,720).options().getFirst().packageId());
        assertEquals(1,plan(d,85,20,720).options().getFirst().requiredStaffCount());
        assertEquals("NO_MATCH",plan(d,130,1,720).status());
        assertEquals(1,plan(d,80,20,120).options().getFirst().packageId());
    }
    @Test void itemQuantityAndWholeTeamsRespectTimeAndStaffLimits() {
        var d=detail("ITEM",pack(1,350000,60,1,80));
        var option=plan(d,160,20,720).options().getFirst();
        assertEquals(2,option.quantity());assertEquals(1,option.requiredStaffCount());assertEquals(700000,option.baseAmount());
        assertEquals(2,plan(d,160,20,60).options().getFirst().requiredStaffCount());
        assertEquals(4,plan(detail("PACKAGE",pack(1,200000,120,2,80)),160,20,720).options().getFirst().requiredStaffCount());
        assertEquals("NO_MATCH",plan(d,2000,1,60).status());
        var rounded=detail("PACKAGE",pack(2,200000,75,1,80));
        assertEquals("NO_MATCH",plan(rounded,80,1,80).status());
        assertEquals(90,plan(rounded,80,1,90).options().getFirst().durationMinutes());
    }
    @Test void unknownCapacityIsNeverTreatedAsUnlimitedAndMeasurementsAreValidated() {
        var d=detail("PACKAGE",pack(1,1,30,1,null),pack(2,195000,180,1,85));
        assertEquals(2,plan(d,80,20,720).options().getFirst().packageId());
        assertEquals(List.of(1L),plan(d,80,20,720).unconfiguredPackageIds());
        assertThrows(ResponseStatusException.class,()->plan(d,0,20,720));
        assertThrows(ResponseStatusException.class,()->plan(d,2001,20,720));
        assertThrows(ResponseStatusException.class,()->plan(d,80,21,720));
        assertThrows(ResponseStatusException.class,()->PackageRecommendationService.plan(d,new BigDecimal("20.5"),20,720));
        assertEquals("NO_MATCH",plan(d,80,20,0).status());
    }
    @Test void quoteCannotBypassAreaCapacityOrUsePartialTeamsAsFullTeams() {
        var p=pack(1,200000,120,2,80);var d=detail("PACKAGE",p);
        assertThrows(ResponseStatusException.class,()->PackageRecommendationService.validateCapacity(d,p,Map.of("areaM2",160),1,3));
        assertDoesNotThrow(()->PackageRecommendationService.validateCapacity(d,p,Map.of("areaM2",160),1,4));
        assertDoesNotThrow(()->PackageRecommendationService.validateCapacity(d,p,Map.of("areaM2",80),1,2));
    }
}
