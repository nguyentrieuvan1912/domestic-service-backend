package com.domesticservice.finance;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:finance-report-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=MONTH")
class FinanceReportApiTests {
    static final HttpServer identity=peer();
    static HttpServer peer() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/identity/auth/me",exchange->{
                String role="Bearer report-admin".equals(exchange.getRequestHeaders().getFirst("Authorization"))?"ADMIN":"CUSTOMER";
                byte[] body=("{\"id\":1,\"role\":\""+role+"\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });server.start();return server;
        } catch(Exception ex){throw new IllegalStateException(ex);}
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry){registry.add("clients.identity.base-url",()->"http://127.0.0.1:"+identity.getAddress().getPort());}
    @AfterAll static void stop(){identity.stop(0);}
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @BeforeEach void schema() {
        for(String table:List.of("payments","withdrawals","refunds","staff_incomes"))db.execute("DROP TABLE IF EXISTS "+table);
        db.execute("CREATE TABLE payments(id bigint,booking_id bigint,customer_id bigint,amount bigint,status varchar,paid_at timestamp with time zone,payment_method varchar,transaction_code varchar,provider_reference varchar,admin_note varchar)");
        db.execute("CREATE TABLE withdrawals(id bigint,staff_id bigint,amount bigint,status varchar,paid_at timestamp with time zone,transfer_reference varchar)");
        db.execute("CREATE TABLE refunds(id bigint,payment_id bigint,amount bigint,status varchar,completed_at timestamp with time zone,transaction_code varchar,reason varchar)");
        db.execute("CREATE TABLE staff_incomes(id bigint,booking_id bigint,staff_id bigint,platform_fee bigint,staff_amount bigint,service_amount bigint,tip_amount bigint,status varchar,settled_at timestamp with time zone)");
        db.update("INSERT INTO payments VALUES(1,11,21,100000,'PAID','2025-12-31T17:00:00Z','CASH','receipt1',null,''),(2,12,22,50000,'REFUNDED','2026-03-01T00:00:00Z','WALLET',null,null,''),(3,13,21,999999,'PENDING','2026-03-01T00:00:00Z','CASH',null,null,''),(4,14,21,777,'PAID','2026-12-31T17:00:00Z','CASH',null,null,''),(5,15,21,40000,'PARTIALLY_REFUNDED','2026-02-01T00:00:00Z','CASH',null,null,'')");
        db.update("INSERT INTO withdrawals VALUES(1,31,20000,'PAID','2026-11-01T00:00:00Z','payout1'),(2,31,30000,'PROCESSING','2026-11-01T00:00:00Z',null)");
        db.update("INSERT INTO refunds VALUES(1,2,10000,'PROCESSED','2026-03-02T00:00:00Z','refund1','Hoàn một phần'),(2,2,100,'APPROVED','2026-03-02T00:00:00Z',null,'Chưa hoàn')");
        db.update("INSERT INTO staff_incomes VALUES(1,11,31,20000,80000,100000,0,'SETTLED','2026-03-02T00:00:00Z'),(2,12,31,1000,9000,10000,0,'PENDING','2026-03-02T00:00:00Z')");
    }
    HttpResponse<String> get(String path,String token)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/finance/admin/"+path));if(token!=null)request.header("Authorization",token);
        return HttpClient.newHttpClient().send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void detailsUseConfirmedDatesAndTheVietnameseYearBoundary()throws Exception {
        var response=get("statistics/details?year=2026","Bearer report-admin");assertEquals(200,response.statusCode(),response.body());
        var rows=json.readTree(response.body());assertEquals(6,rows.size());long revenue=0;
        for(var row:rows){assertTrue(row.get("effectiveAt").asText().contains("T"));if(row.get("kind").asText().equals("REVENUE"))revenue+=row.get("amount").asLong();}
        assertEquals(190000,revenue);assertTrue(response.body().contains("2025-12-31T17:00:00Z"));
        assertFalse(response.body().contains("999999"));assertFalse(response.body().contains("PROCESSING"));
    }
    @Test void detailTotalsReconcileToTheExistingMonthlyStatistics()throws Exception {
        var details=json.readTree(get("statistics/details?year=2026","Bearer report-admin").body());
        var statsResponse=get("statistics?year=2026","Bearer report-admin");assertEquals(200,statsResponse.statusCode(),statsResponse.body());var stats=json.readTree(statsResponse.body());
        for(var mapping:Map.of("REVENUE","revenue","PAYOUT","payouts","REFUND","refunds","FEE","fees").entrySet()) {
            long detailTotal=0,summaryTotal=0;for(var row:details)if(row.get("kind").asText().equals(mapping.getKey()))detailTotal+=row.get("amount").asLong();
            for(var row:stats.get(mapping.getValue()))summaryTotal+=(row.get("amount")!=null?row.get("amount"):row.get("AMOUNT")).asLong();assertEquals(summaryTotal,detailTotal,mapping.getKey());
        }
    }
    @Test void onlyAdminsCanReadReportsAndYearsAreValidated()throws Exception {
        assertEquals(401,get("statistics/details?year=2026",null).statusCode());
        assertEquals(403,get("statistics/details?year=2026","Bearer customer").statusCode());
        assertEquals(400,get("statistics/details?year=1900","Bearer report-admin").statusCode());
    }
}
