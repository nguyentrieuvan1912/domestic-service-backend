package com.domesticservice.identity.service;
import com.domesticservice.identity.repository.PushRepository;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="PUSH_POSTGRES_VERIFY",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.datasource.url=${PUSH_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=call_test_only","spring.jpa.hibernate.ddl-auto=validate","spring.profiles.active=push-verification","EXPO_PUSH_ENABLED=false","identity.bootstrap.email=","identity.bootstrap.phone=","identity.bootstrap.password="})
class PushPostgresTests {
    @Autowired JdbcTemplate db;@Autowired PushService push;@Autowired PushRepository repository;@Autowired PlatformTransactionManager manager;
    long user;String bearer,token;
    @BeforeEach void setup()throws Exception{
        String unique=UUID.randomUUID().toString();user=db.queryForObject("INSERT INTO users(user_name,password_hash,full_name,phone,email,role,status) VALUES (?,'disabled-fixture','Push verification',?,?,'STAFF','ACTIVE') RETURNING id",Long.class,unique,unique.substring(0,20),unique+"@test.invalid");
        String raw=UUID.randomUUID().toString();bearer="Bearer "+raw;String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));db.update("INSERT INTO account_sessions(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 hour')",user,hash);
        token="ExpoPushToken["+unique.replace("-","")+"]";
    }
    @Test void registrationRequiresSessionAndPersistsOnlyValidOwnedDevice(){
        assertThrows(ResponseStatusException.class,()->push.register(null,Map.of("token",token,"platform","android")));
        assertThrows(ResponseStatusException.class,()->push.register(bearer,Map.of("token","fake","platform","android")));
        push.register(bearer,Map.of("token",token,"platform","android"));push.register(bearer,Map.of("token",token,"platform","android"));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM identity_push_devices WHERE user_id=?",Long.class,user));
    }
    @Test void queueDeduplicatesAndRevokedSessionsCannotDeliver(){
        push.register(bearer,Map.of("token",token,"platform","android"));
        long notification=db.queryForObject("INSERT INTO notifications(user_id,notification_type,title,content,payload) VALUES (?,'SCHEDULE_UPDATE','Reminder','Persisted reminder',jsonb_build_object('expiresAt',(now()+interval '20 minutes')::text,'assignmentId',1)) RETURNING id",Long.class,user);
        repository.queue();repository.queue();
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM identity_push_deliveries WHERE notification_id=?",Long.class,notification));
        long delivery=db.queryForObject("SELECT id FROM identity_push_deliveries WHERE notification_id=?",Long.class,notification);
        var tx=new TransactionTemplate(manager);assertEquals(false,tx.execute(state->repository.lock(delivery,"PENDING").getFirst().get("expired")));
        db.update("DELETE FROM account_sessions WHERE user_id=?",user);
        assertEquals(true,tx.execute(state->repository.lock(delivery,"PENDING").getFirst().get("expired")));
    }
}
