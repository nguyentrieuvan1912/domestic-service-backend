package com.domesticservice.identity;

import com.domesticservice.identity.service.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:verificationtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Sql("/accounts-schema.sql")
class RegistrationVerificationTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired AccountService accounts;
    @MockitoBean RegistrationMailer mailer;
    final Map<String,String> delivered=new java.util.concurrent.ConcurrentHashMap<>();
    final HttpClient client=HttpClient.newHttpClient();
    @BeforeEach void setup() {
        delivered.clear();
        doAnswer(invocation->{ delivered.put(invocation.getArgument(0),invocation.getArgument(1)); return null; })
            .when(mailer).send(anyString(),anyString());
    }
    HttpResponse<String> request(String method,String path,Object body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/identity"+path)).header("Content-Type","application/json");
        if (token!=null) builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    String start(String email) throws Exception {
        var response=request("POST","/auth/register/email",Map.of("email",email),null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).doesNotContain(delivered.get(email));
        return json.readTree(response.body()).get("challengeId").asText();
    }
    String verify(String email,String challenge) throws Exception {
        var response=request("POST","/auth/register/verify",Map.of("challengeId",challenge,"code",delivered.get(email)),null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).get("verificationToken").asText();
    }
    Map<String,Object> registration(String proof,String phone,String name) {
        return Map.of("verificationToken",proof,"phone",phone,"fullName",name,"password","Customer!123");
    }
    String register(String proof,String phone,String name) throws Exception {
        var response=request("POST","/auth/register",registration(proof,phone,name),null);
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("accessToken").asText();
    }
    @Test void verificationRequiredAndEmailBoundToProofAndProfilesIsolated() throws Exception {
        assertThat(request("POST","/auth/register",registration("unverified","0900000002","A"),null).statusCode()).isEqualTo(403);
        assertThat(request("POST","/auth/register/email",Map.of("email","someone@example.com"),null).statusCode()).isEqualTo(400);
        String email="customer.one@gmail.com", challenge=start(email);
        assertThat(db.queryForObject("SELECT count(*) FROM customers",Integer.class)).isZero();
        assertThat(delivered.get(email)).matches("[0-9]{6}");
        assertThat(db.queryForObject("SELECT code_hash FROM registration_verifications WHERE email=?",String.class,email)).startsWith("$2").doesNotContain(delivered.get(email));
        String proof=verify(email,challenge);
        var input=new LinkedHashMap<>(registration(proof,"0900000002","Khách một")); input.put("email","attacker@gmail.com");
        var response=request("POST","/auth/register",input,null); assertThat(response.statusCode()).isEqualTo(201);
        String one=json.readTree(response.body()).get("accessToken").asText();
        assertThat(request("POST","/auth/register",registration(proof,"0900000003","Replay"),null).statusCode()).isEqualTo(403);
        assertThat(request("POST","/auth/register/email",Map.of("email",email),null).statusCode()).isEqualTo(409);
        String secondEmail="customer.two@gmail.com";
        String two=register(verify(secondEmail,start(secondEmail)),"0900000003","Khách hai");
        assertThat(request("GET","/auth/profile",null,one).body()).contains(email,"Khách một").doesNotContain("Khách hai","password");
        assertThat(request("PATCH","/customer/profile",Map.of("fullName","Tên mới","phone","0900000002","id",999,"email","attacker@gmail.com"),one).statusCode()).isEqualTo(200);
        assertThat(request("GET","/auth/profile",null,two).body()).contains("Khách hai",secondEmail).doesNotContain("Tên mới");
        assertThat(request("GET","/auth/profile",null,one).body()).contains("Tên mới",email).doesNotContain("attacker@gmail.com");
        assertThat(request("GET","/auth/profile",null,null).statusCode()).isEqualTo(401);
        var login=request("POST","/auth/login",Map.of("identifier",email,"password","Customer!123"),null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(request("GET","/auth/profile",null,json.readTree(login.body()).get("accessToken").asText()).body()).contains("Tên mới");
        assertThat(db.queryForObject("SELECT count(*) FROM customers",Integer.class)).isEqualTo(2);
    }
    @Test void failedAttemptsPersistAndResendInvalidatesOldCode() throws Exception {
        String email="verify.tests@gmail.com", challenge=start(email);
        assertThat(request("POST","/auth/register/email",Map.of("email",email),null).statusCode()).isEqualTo(429);
        String wrong=delivered.get(email).equals("000000")?"000001":"000000";
        for (int i=0;i<5;i++) assertThat(request("POST","/auth/register/verify",Map.of("challengeId",challenge,"code",wrong),null).statusCode()).isEqualTo(400);
        assertThat(db.queryForObject("SELECT attempts FROM registration_verifications WHERE email=?",Integer.class,email)).isEqualTo(5);
        assertThat(request("POST","/auth/register/verify",Map.of("challengeId",challenge,"code",delivered.get(email)),null).statusCode()).isEqualTo(429);
        db.update("UPDATE registration_verifications SET sent_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP) WHERE email=?",email);
        String next=start(email);
        assertThat(request("POST","/auth/register/verify",Map.of("challengeId",challenge,"code",delivered.get(email)),null).statusCode()).isEqualTo(400);
        db.update("UPDATE registration_verifications SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE email=?",email);
        assertThat(request("POST","/auth/register/verify",Map.of("challengeId",next,"code",delivered.get(email)),null).statusCode()).isEqualTo(400);
    }
    @Test void deliveryFailureRollsBackAndHourlyLimitIsEnforced() throws Exception {
        doThrow(AccountService.error(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"SMTP unavailable")).when(mailer).send(eq("failed@gmail.com"),anyString());
        assertThat(request("POST","/auth/register/email",Map.of("email","failed@gmail.com"),null).statusCode()).isEqualTo(503);
        assertThat(db.queryForObject("SELECT count(*) FROM registration_verifications WHERE email='failed@gmail.com'",Integer.class)).isZero();
        String email="limits@gmail.com"; start(email);
        db.update("UPDATE registration_verifications SET send_count=5,sent_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP) WHERE email=?",email);
        assertThat(request("POST","/auth/register/email",Map.of("email",email),null).statusCode()).isEqualTo(429);
    }
    @Test void duplicatePhoneRollsBackProofAndConcurrentRegistrationCannotReplay() throws Exception {
        accounts.bootstrap("admin@test.invalid","0900000001","AdminTest!123");
        String email="rollback@gmail.com", proof=verify(email,start(email));
        assertThat(request("POST","/auth/register",registration(proof,"0900000001","Duplicate phone"),null).statusCode()).isEqualTo(409);
        assertThat(db.queryForObject("SELECT consumed_at FROM registration_verifications WHERE email=?",java.sql.Timestamp.class,email)).isNull();
        var body=registration(proof,"0900000002","Concurrent");
        var results=List.of(CompletableFuture.supplyAsync(()->registerRequest(body)),CompletableFuture.supplyAsync(()->registerRequest(body)));
        assertThat(results.stream().map(CompletableFuture::join).sorted().toList()).containsExactly(201,403);
        assertThat(db.queryForObject("SELECT count(*) FROM customers",Integer.class)).isEqualTo(1);
    }
    @Test void expiredProofCannotCreateCustomer() throws Exception {
        String email="expired@gmail.com", proof=verify(email,start(email));
        db.update("UPDATE registration_verifications SET verified_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE email=?",email);
        assertThat(request("POST","/auth/register",registration(proof,"0900000002","Expired"),null).statusCode()).isEqualTo(403);
        assertThat(db.queryForObject("SELECT count(*) FROM customers",Integer.class)).isZero();
    }
    int registerRequest(Object body) {
        try { return request("POST","/auth/register",body,null).statusCode(); }
        catch(Exception ex) { throw new RuntimeException(ex); }
    }
}
