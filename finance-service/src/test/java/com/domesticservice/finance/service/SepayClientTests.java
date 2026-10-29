package com.domesticservice.finance.service;

import com.domesticservice.finance.config.SepayProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class SepayClientTests {
    HttpServer server;SepayClient client;Map<String,Object> data;int status;boolean authenticated;
    static final String BANK="11111111-1111-4111-8111-111111111111";
    @BeforeEach void setup()throws Exception {
        status=200;authenticated=false;data=new LinkedHashMap<>(Map.of("id",BANK,"account_number","1234567890","account_holder_name","NGUYỄN VĂN A","bank_short_name","TPBank","active",1,"accumulated",10000));
        var json=new ObjectMapper();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{authenticated="Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));byte[] body=json.writeValueAsBytes(Map.of("status","success","data",data));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        var config=new SepayProperties(true,"test-token","x".repeat(32),BANK,"1234567890","NGUYEN VAN A","TKPABC","");
        client=new SepayClient(config,json,HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v2"));
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void sourceIsReadFromProviderAndPinnedToAccountBankNameAndActiveConnection(){assertEquals(10000,client.source().get("accumulated"));assertTrue(authenticated);for(var wrong:List.of(Map.of("account_number","wrong"),Map.of("bank_short_name","OTHER"),Map.of("account_holder_name","OTHER PERSON"),Map.of("active",0))){var original=new LinkedHashMap<>(data);data.putAll(wrong);assertThrows(ResponseStatusException.class,()->client.source());data=original;}}
    @Test void unauthorizedAndNonSuccessHttpResponsesAreNotEvidence(){status=401;assertThrows(ResponseStatusException.class,()->client.source());status=503;assertThrows(ResponseStatusException.class,()->client.source());}
    @Test void detailMustReturnTheRequestedUuid(){String id=UUID.randomUUID().toString();assertThrows(ResponseStatusException.class,()->client.transaction(id));data=new LinkedHashMap<>(Map.of("id",id));assertEquals(id,client.transaction(id).get("id"));}
    @Test void missingConfigurationNeverMakesAnExternalCall(){var config=new SepayProperties(true,"","",BANK,"1234567890","NAME","TKPABC","");var disabled=new SepayClient(config,new ObjectMapper());assertThrows(ResponseStatusException.class,disabled::source);assertFalse(authenticated);}
    @Test void mainAccountReadinessDoesNotRequireMemoVa(){var config=new SepayProperties(true,"test-token","x".repeat(32),BANK,"1234567890","NAME","","");assertTrue(config.ready());var invalid=new SepayProperties(true,"test-token","x".repeat(32),BANK,"1234567890","NAME","invented","");assertFalse(invalid.ready());}
}
