package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayosProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PayosClientTests {
    @Test void virtualAccountsPinBeneficiaryAndBankWhileAllowingPerPaymentAccountNumbers() {
        var client=new PayosClient(new PayosProperties(true,"client","api","checksum","original-account","https://example.com/return","https://example.com/cancel"),new ObjectMapper());
        assertTrue(client.receivingAccountMatches(Map.of("accountNumber","original-account")));
        assertFalse(client.receivingAccountMatches(Map.of("accountNumber","virtual-first")));
        client.configureVirtualAccounts("970000","VERIFIED BENEFICIARY");
        assertTrue(client.receivingAccountMatches(Map.of("accountNumber","virtual-first","bin","970000","accountName","VERIFIED BENEFICIARY")));
        assertTrue(client.receivingAccountMatches(Map.of("accountNumber","virtual-second","bin","970000","accountName","VERIFIED BENEFICIARY")));
        assertFalse(client.receivingAccountMatches(Map.of("accountNumber","virtual-first","bin","970001","accountName","VERIFIED BENEFICIARY")));
        assertFalse(client.receivingAccountMatches(Map.of("accountNumber","virtual-first","bin","970000","accountName","OTHER BENEFICIARY")));
        assertFalse(client.receivingAccountMatches(Map.of("accountNumber","","bin","970000","accountName","VERIFIED BENEFICIARY")));
        client.configureVirtualAccounts("970000","");assertFalse(client.ready());
    }
    @Test void liveMissingOrderAllowsCreationButOtherProviderErrorsDoNot() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var description=new java.util.concurrent.atomic.AtomicReference<>("Mã thanh toán không tồn tại");
        server.createContext("/",exchange->{
            byte[] bytes=new ObjectMapper().writeValueAsBytes(Map.of("code","101","desc",description.get()));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new PayosClient(new PayosProperties(true,"client","api","checksum","123","https://example.com/return","https://example.com/cancel"),new ObjectMapper(),java.net.http.HttpClient.newHttpClient(),java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            assertNull(client.lookup(123));
            description.set("Thông tin xác thực không hợp lệ");
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->client.lookup(123));
        } finally { server.stop(0); }
    }
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
