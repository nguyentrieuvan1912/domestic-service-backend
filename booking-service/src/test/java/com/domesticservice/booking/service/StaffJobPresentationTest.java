package com.domesticservice.booking.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffJobPresentationTest {
    @Test void savedFullAddressWinsAndLegacyPartsKeepNewWard() {
        assertEquals("12 Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh",StaffJobPresentation.address(Map.of("fullAddress","12 Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh","district","Địa bàn cũ")));
        assertEquals("12 Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh",StaffJobPresentation.address(Map.of("detailAddress","12 Nguyễn Huệ","ward","Phường Sài Gòn","district","Phường Sài Gòn","province","TP. Hồ Chí Minh")));
        assertEquals("",StaffJobPresentation.address(Map.of()));
    }
    @Test void detailsPreserveCustomerChoicesAndAmountsWithoutInventingPayment() {
        var result=new LinkedHashMap<String,Object>();
        var booking=Map.<String,Object>of("payment_method","CASH","payment_status","UNPAID","total_amount",243000);
        var service=Map.<String,Object>of("addOns",List.of(Map.of("name","Lau dọn tủ lạnh")),"relatedServices",List.of(Map.of("name","Giặt ủi")),"configuration",Map.of("acNeedGas",false),"configurationLabels",Map.of("acNeedGas","Bổ sung gas"),"answers",Map.of("rooms",List.of("Bếp","Phòng ngủ")),"answerLabels",Map.of("rooms","Khu vực cần dọn"));
        StaffJobPresentation.add(result,booking,service,Map.of("province","TP. Hồ Chí Minh"));
        assertEquals(243000,result.get("totalCustomerPaid"));assertEquals("UNPAID",result.get("paymentStatus"));assertEquals("CASH",result.get("paymentMethod"));
        assertEquals("Lau dọn tủ lạnh",result.get("addOnsText"));assertEquals("Giặt ủi",result.get("relatedServicesText"));
        assertEquals(List.of(Map.of("label","Bổ sung gas","value","Không"),Map.of("label","Khu vực cần dọn","value","Bếp, Phòng ngủ")),result.get("workSelections"));
        assertFalse(result.containsKey("address"));assertFalse(result.containsKey("customerPhone"));
        var summary=new LinkedHashMap<String,Object>();StaffJobPresentation.summary(summary,booking,service,Map.of());
        assertFalse(summary.containsKey("workSelections"));assertFalse(summary.containsKey("address"));
    }
}
