package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated database and HTTP peers only; verifies actual creation SQL, migrations and payment gates. */
@EnabledIfEnvironmentVariable(named="CUSTOMER_PAYMENT_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=BookingPaymentPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.datasource.url=${BOOKING_WALLET_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=topup_test_only","spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=true"})
class BookingPaymentPostgresTests {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
 @Import({NativeQueryExecutor.class,BookingOperationsSqlRepository.class,BookingWorkflowSqlRepository.class,BookingCreationSqlRepository.class,BookingWorkflowService.class,BookingCreationService.class})
 static class Config {
  @Bean(destroyMethod="stop") Peer peer(ObjectMapper json)throws Exception{return new Peer(json);}
  @Bean BookingOperationsService ops(BookingOperationsSqlRepository repo,ObjectMapper json,Peer peer){return new BookingOperationsService(repo,json,peer.url(),peer.url());}
  @Bean CustomerPaymentBridge bridge(NativeQueryExecutor db,BookingWorkflowService flow,BookingOperationsService ops,PlatformTransactionManager manager,Peer peer){return new CustomerPaymentBridge(db,flow,ops,manager,peer.url(),"test-key");}
 }
 static class Peer {
  final HttpServer server;final ObjectMapper json;
  long customer,staff;volatile long paid,refunded;volatile boolean available=true;
  Peer(ObjectMapper json)throws Exception{this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{
   String path=x.getRequestURI().getPath();Object response;
   if(path.contains("customer-payments"))response=Map.of("paidAmount",paid,"refundedAmount",refunded);
   else if(path.contains("auth/me"))response=Map.of("id",customer,"role","CUSTOMER");
   else if(path.contains("staff-directory"))response=available?List.of(Map.of("id",staff,"isOnline",true,"staffStatus","AVAILABLE","fullName","Test staff")):List.of();
   else if(path.contains("addresses"))response=Map.of("id",customer,"city","Ho Chi Minh","ward","Payment test ward");
   else if(path.endsWith("consume"))response=Map.of("consumed",true);
   else response=Map.of("id",customer,"serviceId",customer,"packageId",customer,"durationMinutes",60,"baseAmount",10000,"addOnAmount",0,"discountAmount",0,"totalAmount",10000,"snapshot",Map.of("requiredStaffCount",1,"addOns",List.of(),"name","Payment test service"));
   byte[] bytes=json.writeValueAsBytes(response);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
  });server.start();}
  String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}public void stop(){server.stop(0);}
 }
 @Autowired JdbcTemplate db;@Autowired Peer peer;@Autowired BookingCreationService creation;@Autowired BookingWorkflowService flow;@Autowired CustomerPaymentBridge bridge;
 long customer,staff;Instant start;
 @BeforeEach void setup(){
  customer=ThreadLocalRandom.current().nextLong(10000000,100000000);staff=customer+100000000;
  peer.customer=customer;peer.staff=staff;peer.paid=0;peer.refunded=0;peer.available=true;
  var date=LocalDate.now(BookingOperationsService.ZONE).plusDays(2);start=date.atTime(9,0).atZone(BookingOperationsService.ZONE).toInstant();
  db.update("INSERT INTO staff_schedule_settings(staff_id) VALUES (?)",staff);
  db.update("INSERT INTO staff_schedule_weeks(staff_id,week_start) VALUES (?,?)",staff,date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
  db.update("INSERT INTO staff_availabilities(staff_id,available_date,start_time,end_time) VALUES (?,?,'07:00','20:00')",staff,date);
  db.update("INSERT INTO staff_areas(staff_id,province,ward) VALUES (?,'Ho Chi Minh','Payment test ward')",staff);
  db.update("INSERT INTO staff_service_capabilities(staff_id,service_id,skill_level,status,approved_at) VALUES (?,?,'EXPERT','APPROVED',now())",staff,customer);
 }
 String token(){return "Bearer "+customer;}
 Map<String,Object> create(String method){return creation.create(token(),new LinkedHashMap<>(Map.of("requestKey","payment-test-"+customer,"startsAt",start.toString(),"mode","MODE_B","paymentMethod",method,"addressId",customer)));}
 long id(Map<String,Object> b){return ((Number)b.get("id")).longValue();}
 String status(long id){return db.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id);}
 @Test void creationPersistsMethodAndWaitsForFullPayment(){
  var b=create("WALLET");long id=id(b);assertEquals("AWAITING_PAYMENT",b.get("status"));assertEquals("WALLET",b.get("paymentMethod"));assertEquals(true,b.get("pricingConfirmed"));
  peer.paid=9999;bridge.reconcile(id);assertEquals("AWAITING_PAYMENT",status(id));
  peer.paid=10000;bridge.reconcile(id);assertEquals("MATCHING",status(id));assertEquals("PAID",db.queryForObject("SELECT payment_status FROM bookings WHERE id=?",String.class,id));
  bridge.reconcile(id);assertEquals(1,db.queryForObject("SELECT count(*) FROM booking_status_histories WHERE booking_id=? AND new_status='MATCHING'",Integer.class,id));
 }
 @Test void noEligibleStaffAfterPaymentEndsOrderForRefund(){long id=id(create("BANK_TRANSFER_QR"));peer.paid=10000;peer.available=false;bridge.reconcile(id);assertEquals("NO_STAFF_FOUND",status(id));peer.refunded=10000;bridge.reconcile(id);assertEquals("REFUNDED",db.queryForObject("SELECT payment_status FROM bookings WHERE id=?",String.class,id));}
 @Test void cancellationBeforePaymentCannotBeReopenedByLateReceipt(){long id=id(create("WALLET"));flow.cancel(token(),id,Map.of("reason","Cancel before paying"));peer.paid=10000;bridge.reconcile(id);assertEquals("CANCELLED",status(id));peer.refunded=10000;bridge.reconcile(id);peer.refunded=0;bridge.reconcile(id);assertEquals("REFUNDED",db.queryForObject("SELECT payment_status FROM bookings WHERE id=?",String.class,id));}
 @Test void databaseRejectsAcceptanceBeforePayment(){long id=id(create("BANK_TRANSFER_QR"));assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO booking_assignments(booking_id,staff_id,status) VALUES (?,?,'ACCEPTED')",id,staff));assertEquals(0,db.queryForObject("SELECT count(*) FROM booking_assignments WHERE booking_id=? AND status='ACCEPTED'",Integer.class,id));}
 @Test void cashKeepsExistingMatchingFlow(){assertEquals("MATCHING",create("CASH").get("status"));}
 @Test void favouriteInvitationStartsOnlyAfterPaymentAndDoesNotHoldUnpaidSlot(){
  var body=new LinkedHashMap<String,Object>(Map.of("requestKey","fav-payment-"+customer,"startsAt",start.toString(),"mode","MODE_A","paymentMethod","BANK_TRANSFER_QR","addressId",customer,"staffId",staff));
  var b=creation.create(token(),body);long id=id(b);assertEquals("AWAITING_PAYMENT",b.get("status"));assertNull(b.get("staffResponseDeadline"));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='STAFF_ASSIGNED'",Integer.class,id));
  var candidate=db.queryForMap("SELECT * FROM bookings WHERE id=?",id);candidate.put("id",-1L);candidate.put("booking_mode","MODE_B");
  assertTrue(flow.eligibility(staff,candidate,Map.of("id",staff,"isOnline",true,"staffStatus","AVAILABLE")).isEmpty(),"Unpaid invitation must not occupy staff time");
  peer.paid=10000;bridge.reconcile(id);assertEquals("STAFF_ASSIGNED",status(id));
  var deadline=db.queryForObject("SELECT staff_response_deadline FROM bookings WHERE id=?",Timestamp.class,id);assertTrue(deadline.toInstant().isAfter(Instant.now().plusSeconds(290)));
 }
 @Test void unfilledOrderExpiresAndClosesAssignments(){long id=id(create("WALLET"));db.update("UPDATE bookings SET starts_at=?,ends_at=? WHERE id=?",Timestamp.from(Instant.now().plusSeconds(60)),Timestamp.from(Instant.now().plusSeconds(3660)),id);bridge.expire();assertEquals("NO_STAFF_FOUND",status(id));}
 @Test void internalKeyIsRequired(){assertThrows(ResponseStatusException.class,()->bridge.authorize(null));assertThrows(ResponseStatusException.class,()->bridge.authorize("wrong"));bridge.authorize("test-key");}
}
