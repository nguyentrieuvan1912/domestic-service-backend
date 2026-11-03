package com.domesticservice.finance.service;

import com.domesticservice.finance.config.PayoutProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PayosConnectionErrorTests {
    private String failure(int status, String body) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/payouts-account/balance", exchange -> {
            var bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var config=new PayoutProperties(true,"test-client","test-api","test-checksum","test-account","TEST NAME","970422",1000,1000000);
            var client=new PayosPayoutClient(config,new ObjectMapper(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            return assertThrows(ResponseStatusException.class,client::source).getReason();
        } finally { server.stop(0); }
    }
    @Test void providerIpRejectionExplainsTheRequiredAction() throws Exception {
        assertTrue(failure(403,"{\"code\":\"403\",\"desc\":\"Địa chỉ IP không được phép truy cập hệ thống\"}").contains("IP public của backend"));
    }
    @Test void authenticationAndRateLimitErrorsAreDistinct() throws Exception {
        assertTrue(failure(401,"{}").contains("Client ID"));
        assertTrue(failure(429,"{}").contains("tần suất"));
    }
    @Test void arbitraryProviderBodiesAreNotExposed() throws Exception {
        String message=failure(403,"sensitive upstream response");
        assertFalse(message.contains("sensitive"));
        assertTrue(message.contains("quyền truy cập"));
    }
}
