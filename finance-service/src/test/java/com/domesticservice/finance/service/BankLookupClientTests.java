package com.domesticservice.finance.service;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class BankLookupClientTests {
    final ObjectMapper json=new ObjectMapper();
    HttpServer server; BankLookupClient client; URI base;
    AtomicInteger requests=new AtomicInteger(),lookups=new AtomicInteger();
    Map<String,Object> lastRequest;
    Map<String,Object> data;
    int responseStatus; boolean supported,duplicate;
    @BeforeEach void setup()throws Exception {
        responseStatus=200;supported=true;duplicate=false;data=Map.of("ownerName","NGUYEN VAN A");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{try{
            requests.incrementAndGet();Object payload;int status=200;
            if(exchange.getRequestURI().getPath().equals("/bank/list")){
                var row=Map.of("bin",970422,"code","MB","lookup_supported",supported?1:0);
                payload=Map.of("code",200,"success",true,"data",duplicate?List.of(row,row):List.of(row));
            }else{
                lookups.incrementAndGet();assertEquals("test-key",exchange.getRequestHeaders().getFirst("x-api-key"));assertEquals("test-secret",exchange.getRequestHeaders().getFirst("x-api-secret"));
                lastRequest=json.readValue(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),new TypeReference<Map<String,Object>>(){});
                status=responseStatus;payload=Map.of("code",status,"success",status==200,"data",data);
            }
            byte[] bytes=json.writeValueAsBytes(payload);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        }catch(Throwable ex){exchange.sendResponseHeaders(500,-1);exchange.close();}});
        server.start();base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());client=new BankLookupClient("test-key","test-secret",json,HttpClient.newHttpClient(),base);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void mapsSupportedBinToRealProviderCodeAndPreservesLeadingZeroes(){
        var result=client.lookup("970422","0001234567");assertEquals("NGUYEN VAN A",result.get("accountHolder"));assertEquals(Map.of("bank","MB","account","0001234567"),lastRequest);
        assertEquals("BANKLOOKUP",client.provider());assertTrue(result.get("proof") instanceof Map<?,?> proof && proof.containsKey("request") && proof.containsKey("response"));
        client.lookup("970422","0001234567");assertEquals(3,requests.get());
    }
    @Test void missingCredentialsDoNotCallProvider(){var missing=new BankLookupClient("","",json,HttpClient.newHttpClient(),base);assertFalse(missing.ready());assertThrows(ResponseStatusException.class,()->missing.lookup("970422","0001234567"));assertEquals(0,requests.get());}
    @Test void unsupportedOrAmbiguousBankNeverTriggersLookup(){supported=false;assertThrows(ResponseStatusException.class,()->client.lookup("970422","0001234567"));assertEquals(0,lookups.get());}
    @Test void duplicateBinFailsClosed(){duplicate=true;assertThrows(ResponseStatusException.class,()->client.lookup("970422","0001234567"));assertEquals(0,lookups.get());}
    @Test void paymentAndAuthorizationFailuresCannotReturnAVerifiedName(){for(int status:List.of(402,403,422,429,500)){responseStatus=status;assertThrows(ResponseStatusException.class,()->client.lookup("970422","0001234567"));}}
    @Test void missingOrMismatchedRecipientFailsClosed(){for(var result:List.of(Map.<String,Object>of("ownerName",""),Map.<String,Object>of("ownerName","NGUYEN VAN A","account","wrong"),Map.<String,Object>of("ownerName","NGUYEN VAN A","bank","VCB"))){data=result;assertThrows(ResponseStatusException.class,()->client.lookup("970422","0001234567"));}}
}
