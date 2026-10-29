package com.domesticservice.booking.service;
import com.domesticservice.booking.repository.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CALL_POSTGRES_VERIFY",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.datasource.url=${CALL_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=call_test_only","spring.jpa.hibernate.ddl-auto=validate","spring.profiles.active=call-verification","INTERNAL_SERVICE_TOKEN=call-verification-only"})
@Import(BookingCommunicationPostgresTests.Config.class)
class BookingCommunicationPostgresTests {
    static HttpServer peer;static boolean fail;static final List<String> events=new ArrayList<>();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception{
        peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();String body;
            if(path.endsWith("/internal/events")){events.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body="{\"created\":1}";}
            else {String token=exchange.getRequestHeaders().getFirst("Authorization");String role=token!=null&&token.contains("customer")?"CUSTOMER":"STAFF";int id=token!=null&&token.contains("customer")?101:token!=null&&token.contains("other")?301:201;body="{\"id\":"+id+",\"role\":\""+role+"\",\"fullName\":\"Verification\"}";}
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(fail&&path.endsWith("/internal/events")?503:200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});peer.start();
        registry.add("clients.identity.base-url",()->"http://127.0.0.1:"+peer.getAddress().getPort());
    }
    @TestConfiguration static class Config { @Bean @Primary LiveKitService testLivekit(ObjectMapper json){return new LiveKitService(json,"wss://verification.invalid","verification-key","verification-secret"){@Override public void create(String room){} @Override public void close(String room,long caller,long callee){}};} }
    @Autowired JdbcTemplate db;@Autowired BookingCallService calls;@Autowired BookingReminderService reminders;@Autowired BookingPushValidityRepository validity;
    long booking,assignment,conversation;
    @BeforeEach void prepare(){fail=false;events.clear();
        // Keep the database isolated and each test's due windows independent.
        db.update("UPDATE bookings SET status='CANCELLED'");
        booking=db.queryForObject("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,address_id,service_snapshot,address_snapshot,starts_at,ends_at,base_amount,total_amount,payment_method,pricing_confirmed) VALUES (?,'MODE_A','CONFIRMED',101,1,1,'{}','{}',now()+interval '45 minutes',now()+interval '2 hours',100000,100000,'CASH',true) RETURNING id",Long.class,"communication-"+UUID.randomUUID());
        assignment=db.queryForObject("INSERT INTO booking_assignments(booking_id,staff_id,status,accepted_at) VALUES (?,201,'ACCEPTED',now()) RETURNING id",Long.class,booking);
        conversation=db.queryForObject("INSERT INTO conversations(booking_id,customer_id,staff_id) VALUES (?,101,201) RETURNING id",Long.class,booking);
    }
    @AfterAll static void stop(){if(peer!=null)peer.stop(0);}
    @Test void remindersAreDeduplicatedForBothWindowsAndSkipCancelled(){
        reminders.dispatch();reminders.dispatch();assertEquals(1L,db.queryForObject("SELECT count(*) FROM booking_reminder_deliveries WHERE assignment_id=?",Long.class,assignment));
        db.update("UPDATE bookings SET starts_at=now()+interval '20 minutes' WHERE id=?",booking);reminders.dispatch();reminders.dispatch();
        assertEquals(List.of(30,60),db.queryForList("SELECT minutes_before FROM booking_reminder_deliveries WHERE assignment_id=? ORDER BY minutes_before",Integer.class,assignment));
        db.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",booking);reminders.dispatch();assertEquals(2L,db.queryForObject("SELECT count(*) FROM booking_reminder_deliveries WHERE assignment_id=?",Long.class,assignment));
    }
    @Test void deliveryOutageRollsBackClaimAndRetries(){fail=true;reminders.dispatch();assertEquals(0L,db.queryForObject("SELECT count(*) FROM booking_reminder_deliveries WHERE assignment_id=?",Long.class,assignment));fail=false;reminders.dispatch();assertEquals(1L,db.queryForObject("SELECT count(*) FROM booking_reminder_deliveries WHERE assignment_id=?",Long.class,assignment));}
    @Test void callsAreScopedToAcceptedAssignmentAndRequireCalleeConsent(){
        var body=Map.<String,Object>of("requestKey","call-verification-"+conversation);var call=calls.start("Bearer customer",conversation,body);long id=((Number)call.get("id")).longValue();
        assertEquals(call,calls.start("Bearer customer",conversation,body));
        assertThrows(ResponseStatusException.class,()->calls.get("Bearer other",id));assertThrows(ResponseStatusException.class,()->calls.join("Bearer staff",id));
        assertThrows(ResponseStatusException.class,()->calls.action("Bearer customer",id,Map.of("action","ACCEPT")));
        calls.action("Bearer staff",id,Map.of("action","ACCEPT"));
        String token=calls.join("Bearer staff",id).get("token").toString();String payload=new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),StandardCharsets.UTF_8);
        assertTrue(payload.contains("user-201"));assertFalse(payload.contains("roomCreate"));assertTrue(payload.contains("roomJoin"));
        assertTrue(validity.valid(Map.of("callId",id),201)==false,"Accepted call must not ring a delayed push");
        calls.action("Bearer customer",id,Map.of("action","END"));assertThrows(ResponseStatusException.class,()->calls.join("Bearer staff",id));
        assertEquals("ENDED",calls.get("Bearer staff",id).get("status"));
    }
    @Test void expiredOrCancelledCallsCannotBeAcceptedOrJoined(){
        long id=((Number)calls.start("Bearer staff",conversation,Map.of("requestKey","expire-"+conversation)).get("id")).longValue();
        db.update("UPDATE booking_calls SET created_at=now()-interval '61 seconds' WHERE id=?",id);
        assertThrows(ResponseStatusException.class,()->calls.action("Bearer customer",id,Map.of("action","ACCEPT")));assertEquals("MISSED",calls.get("Bearer staff",id).get("status"));
        db.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",booking);
        assertThrows(ResponseStatusException.class,()->calls.start("Bearer customer",conversation,Map.of("requestKey","cancelled-"+conversation)));
    }
    @Test void reminderValidityFollowsAssignmentAndStartTime(){
        String starts=db.queryForObject("SELECT starts_at FROM bookings WHERE id=?",java.sql.Timestamp.class,booking).toInstant().toString();
        var body=Map.<String,Object>of("assignmentId",assignment,"startsAt",starts);assertTrue(validity.valid(body,201));assertFalse(validity.valid(body,301));
        db.update("UPDATE booking_assignments SET status='CANCELLED' WHERE id=?",assignment);assertFalse(validity.valid(body,201));
    }
}
