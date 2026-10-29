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
    @Test void archivesWholeConversationUsingMostRecentMessageAndReopensOnNewMessage() {
        message("old","2 days");message("recent","23 hours");
        chat.archiveExpired();assertNull(own("Bearer customer").get("customerTrashedAt"));
        db.update("UPDATE messages SET sent_at=now()-interval '25 hours' WHERE conversation_id=?",conversation);
        chat.archiveExpired();assertNotNull(own("Bearer customer").get("customerTrashedAt"));
        assertEquals(2,chat.messages("Bearer customer",conversation).size());
        chat.send("Bearer staff",conversation,Map.of("content","new","clientKey",UUID.randomUUID().toString()));
        assertNull(own("Bearer customer").get("customerTrashedAt"));
        assertEquals(3,chat.messages("Bearer customer",conversation).size());
    }
    @Test void permanentDeletionIsScopedAndDoesNotRestoreOldHistoryWhenNewMessageArrives() {
        message("old one","26 hours");message("old two","25 hours");
        chat.archiveExpired();
        assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer staff",conversation));
        assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer other",conversation));
        chat.deletePermanently("Bearer customer",conversation);
        assertTrue(chat.list("Bearer customer").stream().noneMatch(row->((Number)row.get("id")).longValue()==conversation));
        assertTrue(chat.messages("Bearer customer",conversation).isEmpty());
        assertEquals(2,chat.messages("Bearer staff",conversation).size());
        chat.send("Bearer staff",conversation,Map.of("content","fresh after deletion","clientKey",UUID.randomUUID().toString()));
        assertNull(own("Bearer customer").get("customerTrashedAt"));
        assertEquals(1,chat.messages("Bearer customer",conversation).size());
        assertEquals(3,chat.messages("Bearer staff",conversation).size());
        assertEquals("fresh after deletion",own("Bearer customer").get("lastMessage"));
        assertEquals(1L,((Number)own("Bearer customer").get("unreadCountCustomer")).longValue());
        assertThrows(ResponseStatusException.class,()->chat.deletePermanently("Bearer customer",conversation));
    }
    @Test void emptyArchivedThreadStaysDeletedButItsFirstFutureMessageIsVisible() {
        chat.archiveExpired();chat.deletePermanently("Bearer customer",conversation);chat.archiveExpired();
        assertTrue(chat.list("Bearer customer").stream().noneMatch(row->((Number)row.get("id")).longValue()==conversation));
        chat.send("Bearer staff",conversation,Map.of("content","first","clientKey",UUID.randomUUID().toString()));
        assertEquals(1,chat.messages("Bearer customer",conversation).size());
        assertEquals("first",own("Bearer customer").get("lastMessage"));
    }
}
