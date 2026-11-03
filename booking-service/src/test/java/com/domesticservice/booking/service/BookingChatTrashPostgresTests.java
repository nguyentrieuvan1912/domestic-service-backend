package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.*;
import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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

@EnabledIfEnvironmentVariable(named="CHAT_TRASH_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=BookingChatTrashPostgresTests.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=${CHAT_TRASH_TEST_URL}","spring.datasource.username=${CHAT_TRASH_TEST_USER}","spring.datasource.password=${CHAT_TRASH_TEST_PASSWORD}","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("chat-trash-verification")
class BookingChatTrashPostgresTests {
    @Configuration(proxyBeanMethods=false)
    @Profile("chat-trash-verification")
    @EnableAutoConfiguration
    @EntityScan("com.domesticservice.booking.entity")
    @Import({NativeQueryExecutor.class,BookingOperationsSqlRepository.class,BookingChatSqlRepository.class,BookingChatService.class})
    static class Config {
        @Bean(destroyMethod="stop") IdentityPeer peer() throws Exception { return new IdentityPeer(); }
        @Bean BookingOperationsService operations(BookingOperationsSqlRepository repository,ObjectMapper json,IdentityPeer peer) {
            return new BookingOperationsService(repository,json,peer.url(),peer.url());
        }
    }
    static class IdentityPeer {
        final HttpServer server;
        IdentityPeer() throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String token=Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"),"");
                String role=token.contains("staff")?"STAFF":"CUSTOMER";
                int id=token.contains("staff")?201:token.contains("other")?301:101;
                String body=exchange.getRequestURI().getPath().endsWith("staff-directory")?"[]":"{\"id\":"+id+",\"role\":\""+role+"\",\"fullName\":\"Verification\"}";
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
        }
        String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        void stop(){server.stop(0);}
    }
    @Autowired JdbcTemplate db;
    @Autowired BookingChatService chat;
    long booking,conversation;
    @BeforeEach void setup() {
        booking=db.queryForObject("INSERT INTO bookings(booking_code,booking_mode,status,customer_id,service_id,address_id,service_snapshot,address_snapshot,starts_at,ends_at,base_amount,total_amount,payment_method,pricing_confirmed) VALUES (?,'MODE_A','CONFIRMED',101,1,1,'{}','{}',now()+interval '1 hour',now()+interval '2 hours',100000,100000,'CASH',true) RETURNING id",Long.class,"chat-trash-"+UUID.randomUUID());
        conversation=db.queryForObject("INSERT INTO conversations(booking_id,customer_id,staff_id,created_at) VALUES (?,101,201,now()-interval '3 days') RETURNING id",Long.class,booking);
    }
    void message(String content,String age) {
        db.update("INSERT INTO messages(conversation_id,sender_id,sender_type,message_type,content,sent_at) VALUES (?,201,'STAFF','TEXT',?,now()-CAST(? AS interval))",conversation,content,age);
    }
    Map<String,Object> own(String token) {return chat.list(token).stream().filter(row->((Number)row.get("id")).longValue()==conversation).findFirst().orElseThrow();}
    void complete(String age) {
        db.update("UPDATE bookings SET status='COMPLETED',completed_at=now()-CAST(? AS interval) WHERE id=?",age,booking);
    }
    @Test void idleConversationOfActiveBookingNeverMovesToTrash() {
        message("old","3 days");chat.archiveExpired();
        assertNull(own("Bearer customer").get("customerTrashedAt"));
        assertNull(own("Bearer staff").get("trashedAt"));
        chat.send("Bearer staff",conversation,Map.of("content","still active","clientKey",UUID.randomUUID().toString()));
        assertEquals(2,chat.messages("Bearer customer",conversation).size());
    }
    @Test void archivesFromCompletionEvenIfLastMessageIsRecentAndStaffReadsFirst() {
        message("old","2 days");message("recent","1 hour");complete("25 hours");
        assertNotNull(own("Bearer staff").get("trashedAt"));
        var customer=own("Bearer customer");assertNotNull(customer.get("customerTrashedAt"));
        assertEquals(customer.get("customerTrashedAt"),customer.get("trashedAt"));
        var expected=db.queryForObject("SELECT completed_at+interval '24 hours' FROM bookings WHERE id=?",java.sql.Timestamp.class,booking).toInstant().toString();
        assertEquals(expected,customer.get("trashedAt"));assertEquals(expected,own("Bearer staff").get("trashedAt"));
        assertEquals(2,chat.messages("Bearer customer",conversation).size());
        assertThrows(ResponseStatusException.class,()->chat.send("Bearer staff",conversation,Map.of("content","closed","clientKey",UUID.randomUUID().toString())));
    }
    @Test void recentCompletionKeepsEvenOldMessagesVisibleUntilTwentyFourHours() {
        message("old","3 days");complete("23 hours");chat.archiveExpired();
        assertNull(own("Bearer customer").get("trashedAt"));assertNull(own("Bearer staff").get("trashedAt"));
        complete("24 hours");
        assertNotNull(own("Bearer customer").get("trashedAt"));assertNotNull(own("Bearer staff").get("trashedAt"));
    }
    @Test void missingCompletionTimestampDoesNotGuessFromLastMessage() {
        message("old","3 days");db.update("UPDATE bookings SET status='COMPLETED',completed_at=NULL WHERE id=?",booking);
        chat.archiveExpired();assertNull(own("Bearer customer").get("trashedAt"));assertNull(own("Bearer staff").get("trashedAt"));
    }
    @Test void cancelledBookingIsNotTreatedAsCompleted() {
        message("old","3 days");db.update("UPDATE bookings SET status='CANCELLED',cancelled_at=now()-interval '2 days' WHERE id=?",booking);
        chat.archiveExpired();assertNull(own("Bearer customer").get("trashedAt"));assertNull(own("Bearer staff").get("trashedAt"));
    }
    @Test void permanentDeletionIsScopedAndPreservesStaffAndDatabaseHistory() {
        message("old one","26 hours");message("old two","25 hours");complete("25 hours");chat.archiveExpired();
        assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer staff",conversation));
        assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer other",conversation));
        chat.deletePermanently("Bearer customer",conversation);chat.archiveExpired();
        assertTrue(chat.list("Bearer customer").stream().noneMatch(row->((Number)row.get("id")).longValue()==conversation));
        assertTrue(chat.messages("Bearer customer",conversation).isEmpty());
        assertEquals(2,chat.messages("Bearer staff",conversation).size());
        assertEquals(2,db.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?",Integer.class,conversation));
        assertNotNull(own("Bearer staff").get("trashedAt"));
    }
    @Test void emptyCompletedConversationCanBeTrashedAndDeletedWithoutReappearing() {
        complete("25 hours");chat.archiveExpired();assertNotNull(own("Bearer customer").get("trashedAt"));
        chat.deletePermanently("Bearer customer",conversation);chat.archiveExpired();
        assertTrue(chat.list("Bearer customer").stream().noneMatch(row->((Number)row.get("id")).longValue()==conversation));
        assertNotNull(own("Bearer staff").get("trashedAt"));
    }
    @Test void previousIdleDeadlineIsClearedWithoutRestoringDeletedHistory() {
        message("previously deleted","3 days");
        long cutoff=db.queryForObject("SELECT max(id) FROM messages WHERE conversation_id=?",Long.class,conversation);
        db.update("UPDATE conversations SET customer_trashed_at=now()-interval '1 day',customer_deleted_at=now(),customer_deleted_through_id=? WHERE id=?",cutoff,conversation);
        chat.archiveExpired();assertTrue(chat.messages("Bearer customer",conversation).isEmpty());
        assertNull(db.queryForObject("SELECT customer_trashed_at FROM conversations WHERE id=?",java.sql.Timestamp.class,conversation));
        assertEquals(cutoff,db.queryForObject("SELECT customer_deleted_through_id FROM conversations WHERE id=?",Long.class,conversation));
        chat.send("Bearer staff",conversation,Map.of("content","active new message","clientKey",UUID.randomUUID().toString()));
        assertNull(own("Bearer customer").get("trashedAt"));assertEquals(1,chat.messages("Bearer customer",conversation).size());
    }
    @Test void deletingBeforeDeadlineIsRejected() {
        complete("23 hours");assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer customer",conversation));
    }
}
