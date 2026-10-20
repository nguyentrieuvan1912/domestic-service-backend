package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayosProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PayosClientTests {
    @Test void verifiesOfficialWebhookVectorAndRejectsTampering() {
        var client=new PayosClient(new PayosProperties(true,"client","api","1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675","12345678","https://example.com/return","https://example.com/cancel"),new ObjectMapper());
        var data=new LinkedHashMap<String,Object>();
        data.put("orderCode",123);data.put("amount",3000);data.put("description","VQRIO123");data.put("accountNumber","12345678");
        data.put("reference","TF230204212323");data.put("transactionDateTime","2023-02-04 18:25:00");data.put("currency","VND");
        data.put("paymentLinkId","124c33293c43417ab7879e14c8d9eb18");data.put("code","00");data.put("desc","Thành công");
        for(String key:List.of("counterAccountBankId","counterAccountBankName","counterAccountName","counterAccountNumber","virtualAccountName","virtualAccountNumber")) data.put(key,"");
        assertTrue(client.verify(data,"412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"));
        data.put("amount",300000);assertFalse(client.verify(data,"412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"));
        assertFalse(client.verify(data,"bad"));
    }
    @Test void checkoutIsRestrictedToProvider() {
        assertTrue(StaffTopupService.validCheckout("https://pay.payos.vn/web/example"));
        assertFalse(StaffTopupService.validCheckout("https://pay.payos.vn.evil.test/web/example"));
        assertFalse(StaffTopupService.validCheckout("http://pay.payos.vn/web/example"));
    }
}
