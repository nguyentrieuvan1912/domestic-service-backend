package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import com.domesticservice.booking.controller.JobEvidenceController;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real database, Flyway, immutable image bytes, HTTP controller and transactional chat; Identity is a local test peer. */
@EnabledIfEnvironmentVariable(named="EVIDENCE_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=JobEvidencePostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.datasource.url=${EVIDENCE_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=evidence_test_only","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none"})
class JobEvidencePostgresTests {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
 @Import({NativeQueryExecutor.class,BookingOperationsSqlRepository.class,BookingWorkflowSqlRepository.class,BookingWorkflowService.class,JobEvidenceRepository.class,JobEvidenceService.class,JobEvidenceController.class,BookingChatSqlRepository.class,BookingChatService.class})
 static class Config {
  @Bean(destroyMethod="stop") IdentityPeer peer(ObjectMapper json)throws Exception{return new IdentityPeer(json);}
  @Bean BookingFinanceBridge financeBridge(NativeQueryExecutor db,org.springframework.beans.factory.ObjectProvider<BookingWorkflowService> flow,org.springframework.transaction.PlatformTransactionManager tx,IdentityPeer peer){return new BookingFinanceBridge(db,flow,tx,peer.url(),"settlement-peer-test-only");}
  @Bean BookingOperationsService ops(BookingOperationsSqlRepository repository,ObjectMapper json,IdentityPeer peer){return new BookingOperationsService(repository,json,peer.url(),peer.url());}
 }
 static class IdentityPeer {
  final HttpServer server;volatile boolean failFinance;volatile Map<String,Object> lastFinanceBook;
  IdentityPeer(ObjectMapper json)throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{
   if(x.getRequestURI().getPath().contains("/job-settlement/")){
    lastFinanceBook=json.readValue(x.getRequestBody().readAllBytes(),Map.class);
    Object result=failFinance?Map.of("detail","Finance unavailable in isolated test"):x.getRequestURI().getPath().contains("/reserve/")?Map.of("amount",10000):"COMPLETED".equals(lastFinanceBook.get("status"))?Map.of("invoiceId",101,"status","PAID"):Map.of("status","WAITING");
    var bytes=json.writeValueAsBytes(result);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(failFinance?409:200,bytes.length);x.getResponseBody().write(bytes);x.close();return;
   }
   var token=Objects.toString(x.getRequestHeaders().getFirst("Authorization"),"").replace("Bearer ","");
   long id=token.equals("admin")?1:token.equals("customer")?101:token.equals("other-customer")?102:token.equals("staff")?201:202;
   String role=token.equals("admin")?"ADMIN":token.contains("customer")?"CUSTOMER":"STAFF";
   Object response=x.getRequestURI().getPath().contains("staff-directory")?List.of(Map.of("id",201,"fullName","Evidence test staff")):Map.of("id",id,"role",role,"fullName","Evidence test user");
   var bytes=json.writeValueAsBytes(response);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
  });server.start();}
  String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}public void stop(){server.stop(0);}
 }
 @Autowired IdentityPeer peer;@Autowired BookingFinanceBridge bridge;
 @Autowired JdbcTemplate db;@Autowired JobEvidenceService service;@Autowired BookingWorkflowService flow;@Autowired BookingChatService chat;@Autowired ObjectMapper json;
 @Value("${local.server.port}") int port;
 long booking,assignment,conversation;String before,after;
 @BeforeEach void seed()throws Exception{
  peer.failFinance=false;
  booking=db.queryForObject("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,address_id,service_snapshot,address_snapshot,starts_at,ends_at,required_staff_count,base_amount,total_amount,payment_method,pricing_confirmed) VALUES (?,'MODE_B','IN_PROGRESS',101,1,1,'{\"name\":\"Evidence test\",\"workflow\":[]}', '{}',?,?,1,10000,10000,'CASH',true) RETURNING id",Long.class,"evidence-"+UUID.randomUUID(),Timestamp.from(Instant.now().plusSeconds(3600)),Timestamp.from(Instant.now().plusSeconds(7200)));
  assignment=db.queryForObject("INSERT INTO booking_assignments(booking_id,staff_id,status,execution_status,accepted_at) VALUES (?,201,'ACCEPTED','IN_PROGRESS',now()) RETURNING id",Long.class,booking);
  conversation=db.queryForObject("INSERT INTO conversations(booking_id,staff_id,customer_id) VALUES (?,201,101) RETURNING id",Long.class,booking);
  before=photo(0xFF2244);after=photo(0x22AA44);
 }
 static String photo(int color)throws Exception{var image=new BufferedImage(24,16,BufferedImage.TYPE_INT_RGB);for(int y=0;y<16;y++)for(int x=0;x<24;x++)image.setRGB(x,y,color+x);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return Base64.getEncoder().encodeToString(bytes.toByteArray());}
 Map<String,Object> body(String stage,String key,String image){return Map.of("stage",stage,"requestKey",key,"imageBase64",image);}
 Map<String,Object> upload(String stage,String key,String image){return service.upload("Bearer staff",booking,body(stage,key,image));}
 long count(String table){return db.queryForObject("SELECT count(*) FROM "+table+" WHERE booking_id=?",Long.class,booking);}
 @Test void completionRequiresBothStagesAndPhotosAreSentToThisBookingsChat(){
  assertThrows(ResponseStatusException.class,()->flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true)));
  var first=upload("BEFORE","before",before);
  assertThrows(ResponseStatusException.class,()->flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true)));
  upload("AFTER","after",after);flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true));
  assertEquals("COMPLETED",db.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking));
  var messages=chat.messages("Bearer customer",conversation);assertEquals(2,messages.size());
  assertTrue(messages.stream().allMatch(m->"IMAGE".equals(m.get("contentType"))&&m.get("mediaUrl").toString().startsWith("data:image/jpeg;base64,")&&((Number)m.get("evidenceBookingId")).longValue()==booking));
  assertEquals(2,service.list("Bearer admin",booking).size());
  assertEquals(first.get("id"),upload("BEFORE","before",before).get("id"),"Lost upload responses can be recovered after completion without creating another image/message");
  assertThrows(ResponseStatusException.class,()->upload("AFTER","new-image",before));
 }
 @Test void originalImageIsDurableOwnedAndImmutable(){
  var saved=upload("BEFORE","original",before);long id=((Number)saved.get("id")).longValue();
  for(String actor:List.of("staff","customer","admin"))assertArrayEquals(Base64.getDecoder().decode(before),(byte[])service.image("Bearer "+actor,booking,id).get("image_data"));
  assertThrows(ResponseStatusException.class,()->service.list("Bearer other-customer",booking));assertThrows(ResponseStatusException.class,()->service.image("Bearer other-staff",booking,id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE booking_job_evidence SET stage='AFTER' WHERE id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM booking_job_evidence WHERE id=?",id));
  assertEquals(1,count("booking_job_evidence"));
  assertTrue(db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_status_histories WHERE booking_id=? AND changed_by=201 AND reason LIKE ?)",Boolean.class,booking,"%#"+id));
 }
 @Test void duplicateFilesAndReusedKeysCannotSatisfyTwoPhotoRequirement(){
  upload("BEFORE","same-key",before);
  assertThrows(ResponseStatusException.class,()->upload("AFTER","another-key",before));
  assertThrows(ResponseStatusException.class,()->upload("AFTER","same-key",after));
  assertEquals(1,count("booking_job_evidence"));assertEquals(1,chat.messages("Bearer customer",conversation).size());
 }
 @Test void concurrentRetrySavesExactlyOneImageAndOneChatMessage()throws Exception{
  try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(()->upload("BEFORE","race",before));var two=pool.submit(()->upload("BEFORE","race",before));assertEquals(one.get().get("id"),two.get().get("id"));}
  assertEquals(1,count("booking_job_evidence"));assertEquals(1,chat.messages("Bearer customer",conversation).size());
 }
 @Test void invalidFilesAndUnauthorizedUploadsCannotCreateEvidence(){
  assertThrows(ResponseStatusException.class,()->upload("BEFORE","text",Base64.getEncoder().encodeToString("not an image".getBytes())));
  assertThrows(ResponseStatusException.class,()->upload("BEFORE","too-large","a".repeat(2796205)));
  assertThrows(ResponseStatusException.class,()->service.upload("Bearer other-staff",booking,body("BEFORE","outsider",before)));
  assertThrows(ResponseStatusException.class,()->service.upload("Bearer customer",booking,body("BEFORE","customer",before)));
  assertThrows(ResponseStatusException.class,()->service.upload("Bearer admin",booking,body("BEFORE","admin",before)));
  assertEquals(0,count("booking_job_evidence"));assertEquals(0,chat.messages("Bearer customer",conversation).size());
 }
 @Test void directDatabaseCompletionAlsoRequiresEvidence(){assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE booking_assignments SET execution_status='COMPLETED',status='COMPLETED' WHERE id=?",assignment));assertEquals("IN_PROGRESS",db.queryForObject("SELECT execution_status FROM booking_assignments WHERE id=?",String.class,assignment));}
 @Test void anotherWorkerCannotUseTheFirstWorkersPhotos(){
  db.update("UPDATE bookings SET required_staff_count=2 WHERE id=?",booking);
  db.update("INSERT INTO booking_assignments(booking_id,staff_id,status,execution_status,accepted_at) VALUES (?,202,'ACCEPTED','IN_PROGRESS',now())",booking);
  db.update("INSERT INTO conversations(booking_id,staff_id,customer_id) VALUES (?,202,101)",booking);
  upload("BEFORE","before",before);upload("AFTER","after",after);
  assertThrows(ResponseStatusException.class,()->flow.progress("Bearer other-staff",booking,Map.of("status","COMPLETED","cashReceived",true)));
  assertTrue(service.list("Bearer other-staff",booking).isEmpty());assertEquals(2,service.list("Bearer customer",booking).size());
 }
 @Test void arrivalAllowsBeforeButAfterRequiresWorkToHaveStarted(){
  db.update("UPDATE booking_assignments SET execution_status='EN_ROUTE' WHERE id=?",assignment);db.update("UPDATE bookings SET status='CONFIRMED' WHERE id=?",booking);
  upload("BEFORE","arrival",before);assertThrows(ResponseStatusException.class,()->upload("AFTER","too-soon",after));
  flow.progress("Bearer staff",booking,Map.of("status","IN_PROGRESS"));upload("AFTER","finished",after);assertEquals(2,count("booking_job_evidence"));
 }
 @Test void customerChatDeletionDoesNotDestroyAdminEvidence(){
  upload("BEFORE","before",before);db.update("UPDATE conversations SET customer_trashed_at=now() WHERE id=?",conversation);chat.deletePermanently("Bearer customer",conversation);
  assertTrue(chat.messages("Bearer customer",conversation).isEmpty());assertEquals(1,service.list("Bearer admin",booking).size());assertEquals(1,chat.messages("Bearer staff",conversation).size());
 }
 @Test void cashCompletionRequiresExplicitCollectionAndPersistsAudit(){
  upload("BEFORE","cash-before",before);upload("AFTER","cash-after",after);
  assertThrows(ResponseStatusException.class,()->flow.progress("Bearer staff",booking,Map.of("status","COMPLETED")));
  assertEquals("IN_PROGRESS",db.queryForObject("SELECT execution_status FROM booking_assignments WHERE id=?",String.class,assignment));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE booking_assignments SET execution_status='COMPLETED' WHERE id=?",assignment));
  flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true));
  assertNotNull(db.queryForObject("SELECT cash_received_at FROM booking_assignments WHERE id=?",Timestamp.class,assignment));
 }
 @Test void financeFailureDoesNotCompleteCashAndDurableSettlementRetries(){
  upload("BEFORE","finance-before",before);upload("AFTER","finance-after",after);
  peer.failFinance=true;
  assertThrows(ResponseStatusException.class,()->flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true)));
  assertEquals("IN_PROGRESS",db.queryForObject("SELECT execution_status FROM booking_assignments WHERE id=?",String.class,assignment));
  assertNull(db.queryForObject("SELECT cash_received_at FROM booking_assignments WHERE id=?",Timestamp.class,assignment));
  peer.failFinance=false;flow.progress("Bearer staff",booking,Map.of("status","COMPLETED","cashReceived",true));
  assertTrue(db.queryForObject("SELECT finance_sync_required FROM bookings WHERE id=?",Boolean.class,booking));
  peer.failFinance=true;bridge.reconcile();
  assertTrue(db.queryForObject("SELECT finance_sync_required FROM bookings WHERE id=?",Boolean.class,booking));
  assertNotNull(db.queryForObject("SELECT finance_sync_error FROM bookings WHERE id=?",String.class,booking));
  peer.failFinance=false;bridge.reconcile();
  assertFalse(db.queryForObject("SELECT finance_sync_required FROM bookings WHERE id=?",Boolean.class,booking));
  assertNotNull(db.queryForObject("SELECT finance_settled_at FROM bookings WHERE id=?",Timestamp.class,booking));
  assertEquals("PAID",db.queryForObject("SELECT payment_status FROM bookings WHERE id=?",String.class,booking));
 }
 @Test void realHttpEndpointsReturnSavedPhotosAndRejectMissingAuthentication()throws Exception{
  var http=HttpClient.newHttpClient();String url="http://localhost:"+port+"/api/v1/booking/bookings/"+booking+"/evidence";
  var response=http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer staff").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body("BEFORE","http",before)))).build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,response.statusCode());var saved=json.readValue(response.body(),Map.class);long id=((Number)saved.get("id")).longValue();
  var image=http.send(HttpRequest.newBuilder(URI.create(url+"/"+id+"/image")).header("Authorization","Bearer customer").GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,image.statusCode());assertArrayEquals(Base64.getDecoder().decode(before),image.body());
  assertEquals(401,http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
 }
}
