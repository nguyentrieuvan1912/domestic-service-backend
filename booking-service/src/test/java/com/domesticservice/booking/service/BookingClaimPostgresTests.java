package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL transactions, row locks, reservations and workflow. Only the identity HTTP peer is isolated. */
@EnabledIfEnvironmentVariable(named="JPA_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=BookingClaimPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("booking-claim-verification")
class BookingClaimPostgresTests {
    @Configuration(proxyBeanMethods=false)
    @Profile("booking-claim-verification")
    @EnableAutoConfiguration
    @EntityScan("com.domesticservice.booking.entity")
    @Import({NativeQueryExecutor.class,BookingOperationsSqlRepository.class,BookingWorkflowSqlRepository.class,BookingWorkflowService.class,BookingCreationSqlRepository.class,StaffResponseSqlRepository.class})
    static class Config {
        @Bean(destroyMethod="stop") IdentityPeer identityPeer() throws Exception { return new IdentityPeer(); }
        @Bean BookingOperationsService operations(BookingOperationsSqlRepository repository, ObjectMapper json, IdentityPeer peer) {
            return new BookingOperationsService(repository,json,peer.url(),peer.url());
        }
    }
    static class IdentityPeer {
        final HttpServer server;
        IdentityPeer() throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String token=exchange.getRequestHeaders().getFirst("Authorization");
                long id=Long.parseLong(token.replace("Bearer ",""));
                byte[] bytes=("{\"id\":"+id+",\"role\":\"STAFF\",\"status\":\"ACTIVE\",\"staffStatus\":\"AVAILABLE\",\"isOnline\":true,\"fullName\":\"Concurrency verification\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
        }
        String url(){ return "http://127.0.0.1:"+server.getAddress().getPort(); }
        public void stop(){ server.stop(0); }
    }
    @Autowired JdbcTemplate db;
    @Autowired BookingWorkflowService workflow;
    long seed; LocalDate date; Instant start;
    final List<Long> bookings=new ArrayList<>();

    @BeforeEach void prepare() {
        seed=1_000_000_000L+ThreadLocalRandom.current().nextInt(900_000_000);
        date=LocalDate.now(BookingOperationsService.ZONE).plusDays(2);
        start=date.atTime(9,0).atZone(BookingOperationsService.ZONE).toInstant();
        for(int i=0;i<3;i++) {
            long staff=seed+i;
            db.update("INSERT INTO staff_schedule_settings(staff_id) VALUES (?)",staff);
            db.update("INSERT INTO staff_schedule_weeks(staff_id,week_start) VALUES (?,?)",staff,date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            db.update("INSERT INTO staff_availabilities(staff_id,available_date,start_time,end_time) VALUES (?,?,'07:00','20:00')",staff,date);
            db.update("INSERT INTO staff_areas(staff_id,province,ward) VALUES (?,'Ho Chi Minh','Verification ward')",staff);
            db.update("INSERT INTO staff_service_capabilities(staff_id,service_id,skill_level,status,approved_at) VALUES (?,?,'EXPERT','APPROVED',now())",staff,seed);
        }
    }
    @AfterEach void cleanup() {
        for(long booking:bookings) {
            db.update("DELETE FROM outbox_events WHERE aggregate_type='BOOKING' AND aggregate_id=?",booking);
            for(String table:List.of("conversations","staff_reservations","booking_status_histories","booking_assignments"))
                db.update("DELETE FROM "+table+" WHERE booking_id=?",booking);
            db.update("DELETE FROM bookings WHERE id=?",booking);
        }
        bookings.clear();
        for(String table:List.of("staff_service_capabilities","staff_areas","staff_availabilities","staff_schedule_weeks","staff_schedule_settings"))
            db.update("DELETE FROM "+table+" WHERE staff_id BETWEEN ? AND ?",seed,seed+2);
    }
    long booking(int capacity) {
        Long id=db.queryForObject("""
            INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,address_id,
              service_snapshot,address_snapshot,starts_at,ends_at,required_staff_count,base_amount,total_amount,payment_method,pricing_confirmed)
            VALUES (?,'MODE_B','MATCHING',?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,100000,100000,'CASH',true) RETURNING id
            """,Long.class,"claim-test-"+UUID.randomUUID(),seed,seed,seed,
            "{\"name\":\"Verification\",\"workflow\":[\"Prepare\"],\"answers\":{\"need\":\"Persisted request\"}}",
            "{\"city\":\"Ho Chi Minh\",\"ward\":\"Verification ward\"}",Timestamp.from(start),Timestamp.from(start.plusSeconds(3600)),capacity);
        bookings.add(id);return id;
    }
    boolean claim(long staff,long booking) {
        try { workflow.claim("Bearer "+staff,booking); return true; }
        catch(ResponseStatusException ex) { assertEquals(409,ex.getStatusCode().value()); return false; }
    }
    List<Boolean> race(List<Callable<Boolean>> tasks) throws Exception {
        var barrier=new CyclicBarrier(tasks.size());
        try(var executor=Executors.newFixedThreadPool(tasks.size())) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var task:tasks) futures.add(executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);return task.call();}));
            List<Boolean> results=new ArrayList<>();
            for(var future:futures) results.add(future.get(30,TimeUnit.SECONDS));
            return results;
        }
    }
    long accepted(long id){return db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status='ACCEPTED'",Long.class,id);}

    @Autowired BookingCreationSqlRepository creationRepository;
    @Autowired StaffResponseSqlRepository responseRepository;

    @Test void initialAndRenewedInvitationWindowsAreFiveMinutes() {
        long id=booking(1);
        db.update("UPDATE bookings SET booking_mode='MODE_A' WHERE id=?",id);
        creationRepository.createUpdateBookings(id);
        Double initial=db.queryForObject("SELECT extract(epoch FROM (staff_response_deadline-clock_timestamp())) FROM bookings WHERE id=?",Double.class,id);
        assertNotNull(initial);assertTrue(initial>295 && initial<=300,"Initial deadline must be five minutes");
        responseRepository.decideUpdateBookings2(id);
        Double renewed=db.queryForObject("SELECT extract(epoch FROM (staff_response_deadline-clock_timestamp())) FROM bookings WHERE id=?",Double.class,id);
        assertNotNull(renewed);assertTrue(renewed>295 && renewed<=300,"Renewal deadline must be five minutes");
        assertEquals(2,db.queryForObject("SELECT staff_wait_round FROM bookings WHERE id=?",Integer.class,id));
    }

    @Test void twoStaffRaceForOnePosition() throws Exception {
        long id=booking(1);
        assertEquals(1,race(List.of(()->claim(seed,id),()->claim(seed+1,id))).stream().filter(Boolean::booleanValue).count());
        assertEquals(1,accepted(id));
        assertEquals("CONFIRMED",db.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM staff_reservations WHERE booking_id=?",Integer.class,id));
    }
    @Test void multipleStaffPackageHonoursExactCapacity() throws Exception {
        long id=booking(2);
        assertEquals(2,race(List.of(()->claim(seed,id),()->claim(seed+1,id),()->claim(seed+2,id))).stream().filter(Boolean::booleanValue).count());
        assertEquals(2,accepted(id));
    }
    @Test void sameStaffCannotTakeTwoOverlappingJobs() throws Exception {
        long first=booking(1),second=booking(1);
        assertEquals(1,race(List.of(()->claim(seed,first),()->claim(seed,second))).stream().filter(Boolean::booleanValue).count());
    }
    @Test void retryOfSameClaimIsIdempotent() throws Exception {
        long id=booking(1);
        assertEquals(List.of(true,true),race(List.of(()->claim(seed,id),()->claim(seed,id))));
        assertEquals(1,accepted(id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM conversations WHERE booking_id=?",Integer.class,id));
    }
    @Test void serverFeedFiltersAreaSkillsAndRegisteredAvailability() {
        long valid=booking(1),outside=booking(1),wrongSkill=booking(1),outsideHours=booking(1);
        db.update("UPDATE bookings SET address_snapshot='{\"city\":\"Other city\",\"ward\":\"Verification ward\"}' WHERE id=?",outside);
        db.update("UPDATE bookings SET service_snapshot=jsonb_set(service_snapshot,'{relatedServices}','[{\"id\":9999999999}]') WHERE id=?",wrongSkill);
        db.update("UPDATE bookings SET starts_at=starts_at+interval '12 hours',ends_at=ends_at+interval '12 hours' WHERE id=?",outsideHours);
        var items=(List<?>)workflow.matching("Bearer "+seed).get("items");
        var ids=items.stream().map(item->((Number)((Map<?,?>)((Map<?,?>)item).get("opportunity")).get("id")).longValue()).toList();
        assertEquals(List.of(valid),ids);
        db.update("DELETE FROM staff_schedule_weeks WHERE staff_id=?",seed);
        assertTrue(((List<?>)workflow.matching("Bearer "+seed).get("items")).isEmpty());
    }
    @Test void databaseGuardAlsoProtectsWritersWithoutWorkflowLock() throws Exception {
        long id=booking(1);
        List<Callable<Boolean>> inserts=new ArrayList<>();
        for(long staff=seed;staff<=seed+1;staff++) {
            final long actor=staff;
            inserts.add(()->{
                try {db.update("INSERT INTO booking_assignments(booking_id,staff_id,status) VALUES (?,?,'ACCEPTED')",id,actor);return true;}
                catch(org.springframework.dao.DataIntegrityViolationException ex){assertTrue(ex.getMessage().contains("no remaining staff positions"));return false;}
            });
        }
        assertEquals(1,race(inserts).stream().filter(Boolean::booleanValue).count());
        assertEquals(1,accepted(id));
    }
}
