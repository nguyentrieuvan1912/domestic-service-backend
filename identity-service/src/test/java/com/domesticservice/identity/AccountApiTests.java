package com.domesticservice.identity;

import com.domesticservice.identity.service.AccountService;
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
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:accounttests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Sql("/accounts-schema.sql")
class AccountApiTests {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newHttpClient();
    String admin;
    @BeforeEach void setup() throws Exception {
        accounts.bootstrap("admin@test.invalid","0900000001","AdminTest!123");
        admin=token(request("POST","/auth/login",Map.of("identifier","admin@test.invalid","password","AdminTest!123"),null));
    }
    HttpResponse<String> request(String method,String path,Object body,String token) throws Exception {
        // A verified DB fixture keeps existing account/recruitment tests focused on their concerns.
        // RegistrationVerificationTests exercises the entire public verification flow.
        if ("/auth/register".equals(path) && body instanceof Map<?,?> values && values.get("email") instanceof String email) {
            var input=new LinkedHashMap<String,Object>(); values.forEach((key,value)->input.put(key.toString(),value));
            String proof=UUID.randomUUID().toString();
            db.update("DELETE FROM registration_verifications WHERE email=?",email);
            db.update("INSERT INTO registration_verifications(email,challenge_id,code_hash,expires_at,sent_at,window_started_at,verification_token_hash,verified_until) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)",
                email,UUID.randomUUID().toString(),"test-fixture",com.domesticservice.identity.service.RegistrationVerificationService.digest(proof),java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(1200)));
            input.put("verificationToken",proof); body=input;
        }
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/identity"+path))
            .header("Content-Type","application/json");
        if (token!=null) builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    String token(HttpResponse<String> response) { assertThat(response.statusCode()).isBetween(200,201); return json.readTree(response.body()).get("accessToken").asText(); }
    Map<String,Object> registration() { return Map.of("fullName","Khách thử nghiệm","phone","0900000002","email","customer@test.invalid","password","Customer!123","role","ADMIN"); }
    @Test void customerAddressesPersistAndEnforceOwnership() throws Exception {
        String customer=token(request("POST","/auth/register",registration(),null));
        String other=token(request("POST","/auth/register",Map.of("fullName","Khách khác","phone","0900000010","email","other@test.invalid","password","Customer!123"),null));
        var body=Map.of("title","Nhà riêng","recipientName","Người nhận","recipientPhone","0900000002","streetAddress","123 Đường thử","district","Quận 1","city","TP. Hồ Chí Minh");
        assertThat(request("POST","/customer/addresses",body,null).statusCode()).isEqualTo(401);
        assertThat(request("POST","/customer/addresses",body,admin).statusCode()).isEqualTo(403);
        var first=request("POST","/customer/addresses",body,customer);
        assertThat(first.statusCode()).isEqualTo(201);
        long id=json.readTree(first.body()).get("id").asLong();
        assertThat(json.readTree(first.body()).get("isDefault").asBoolean()).isTrue();
        long second=json.readTree(request("POST","/customer/addresses",body,customer).body()).get("id").asLong();
        assertThat(request("GET","/customer/addresses/"+id,null,other).statusCode()).isEqualTo(404);
        assertThat(request("PUT","/customer/addresses/"+id,body,other).statusCode()).isEqualTo(404);
        assertThat(request("DELETE","/customer/addresses/"+id,null,other).statusCode()).isEqualTo(404);
        assertThat(request("PATCH","/customer/addresses/"+id+"/default",null,other).statusCode()).isEqualTo(404);
        assertThat(request("PATCH","/customer/addresses/"+second+"/default",null,customer).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("SELECT count(*) FROM addresses WHERE is_default=true",Integer.class)).isEqualTo(1);
        var changed=new LinkedHashMap<String,Object>(body); changed.put("streetAddress","456 Địa chỉ đã sửa");
        assertThat(request("PUT","/customer/addresses/"+id,changed,customer).statusCode()).isEqualTo(200);
        assertThat(request("GET","/customer/addresses",null,customer).body()).contains("456 Địa chỉ đã sửa");
        assertThat(request("GET","/auth/profile",null,customer).body()).contains("\"addressCount\":2");
        assertThat(request("DELETE","/customer/addresses/"+second,null,customer).statusCode()).isEqualTo(204);
        assertThat(db.queryForObject("SELECT is_default FROM addresses WHERE id=?",Boolean.class,id)).isTrue();
        assertThat(request("GET","/customer/addresses",null,other).body()).isEqualTo("[]");
    }
    @Test void notificationsReadStateAndSupportAreStored() throws Exception {
        String customer=token(request("POST","/auth/register",registration(),null));
        long owner=json.readTree(request("GET","/auth/me",null,customer).body()).get("id").asLong();
        db.update("INSERT INTO notifications(user_id,notification_type,title,content) VALUES (?,'SYSTEM','Thông báo thật','Nội dung riêng')",owner);
        db.update("INSERT INTO notifications(user_id,notification_type,title,content) VALUES (1,'SYSTEM','Riêng admin','Không được đọc')");
        var list=request("GET","/notifications",null,customer);
        assertThat(list.statusCode()).isEqualTo(200); assertThat(list.body()).contains("Thông báo thật").doesNotContain("Riêng admin");
        long id=json.readTree(list.body()).get(0).get("id").asLong();
        assertThat(request("GET","/notifications/"+id,null,admin).statusCode()).isEqualTo(404);
        assertThat(request("PATCH","/notifications/"+id+"/read",null,admin).statusCode()).isEqualTo(404);
        assertThat(request("PATCH","/notifications/"+id+"/read",null,customer).body()).contains("\"changed\":1");
        assertThat(request("PATCH","/notifications/"+id+"/read",null,customer).body()).contains("\"changed\":0");
        assertThat(request("GET","/notifications/"+id,null,customer).body()).contains("\"isRead\":true");
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=1 AND read_at IS NOT NULL",Integer.class)).isZero();
        assertThat(request("POST","/support/requests",Map.of("name","Người gửi","email","contact@test.invalid","subject","Cần hỗ trợ","message","Lời nhắn thật"),null).statusCode()).isEqualTo(201);
        assertThat(db.queryForObject("SELECT message FROM support_requests",String.class)).isEqualTo("Lời nhắn thật");
    }
    long candidate() throws Exception {
        var body=new LinkedHashMap<String,Object>();
        body.put("fullName","Ứng viên thử nghiệm");body.put("phone","0900000003");body.put("email","staff@test.invalid");
        body.put("cccd","012345678901");body.put("birthYear",1998);body.put("dateOfBirth","1998-06-18");body.put("experienceYears",3);body.put("gender","Nữ");body.put("area","Quận 1");
        body.put("source","Website");body.put("services",List.of("Dọn dẹp"));body.put("note","Đăng ký thử");
        body.put("status","EVALUATION");body.put("staffId",999);
        var response=request("POST","/admin/candidates",body,admin);
        assertThat(response.statusCode()).isEqualTo(201);
        var result=json.readTree(response.body()); assertThat(result.get("status").asText()).isEqualTo("PENDING");
        assertThat(result.get("staffId").isNull()).isTrue();return result.get("id").asLong();
    }
    void evaluate(long id) throws Exception {
        assertThat(request("PATCH","/admin/candidates/"+id,Map.of("status","ONBOARDING"),admin).statusCode()).isEqualTo(200);
        assertThat(request("PATCH","/admin/candidates/"+id,Map.of("status","TRAINING","documentChecklist",List.of("Bản sao CCCD","Hộ khẩu / KT3","Giấy khám sức khỏe"),"onboardingNote","Hợp lệ"),admin).statusCode()).isEqualTo(200);
        assertThat(request("PATCH","/admin/candidates/"+id,Map.of("status","EVALUATION","trainingScore",85,"trainingNotes","Đạt yêu cầu"),admin).statusCode()).isEqualTo(200);
    }
    @Test void customerAuthRoleUniquenessAndRevocation() throws Exception {
        assertThat(request("GET","/admin/customers",null,null).statusCode()).isEqualTo(401);
        String customer=token(request("POST","/auth/register",registration(),null));
        var me=request("GET","/auth/me",null,customer);
        assertThat(me.statusCode()).isEqualTo(200); assertThat(me.body()).contains("CUSTOMER").doesNotContain("password_hash");
        assertThat(request("GET","/admin/customers",null,customer).statusCode()).isEqualTo(403);
        assertThat(request("POST","/auth/register",registration(),null).statusCode()).isEqualTo(409);
        assertThat(request("POST","/auth/login",Map.of("identifier","customer@test.invalid","password","wrong"),null).statusCode()).isEqualTo(401);
        long id=json.readTree(me.body()).get("id").asLong();
        assertThat(request("PATCH","/admin/customers/"+id+"/status",Map.of("status","LOCKED","statusNote","Kiểm tra khóa"),admin).statusCode()).isEqualTo(204);
        assertThat(request("GET","/auth/me",null,customer).statusCode()).isEqualTo(401);
        assertThat(request("POST","/auth/login",Map.of("identifier","0900000002","password","Customer!123"),null).statusCode()).isEqualTo(401);
        assertThat(request("PATCH","/admin/customers/"+id+"/status",Map.of("status","ACTIVE"),admin).statusCode()).isEqualTo(204);
        String next=token(request("POST","/auth/login",Map.of("identifier","0900000002","password","Customer!123"),null));
        assertThat(request("POST","/auth/logout",null,next).statusCode()).isEqualTo(204);
        assertThat(request("GET","/auth/me",null,next).statusCode()).isEqualTo(401);
        assertThat(db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id)).startsWith("$2").doesNotContain("Customer!123");
    }
    @Test void sessionExpirationAndInvalidPasswordAreRejected() throws Exception {
        String customer=token(request("POST","/auth/register",registration(),null));
        db.update("UPDATE account_sessions SET expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE user_id=(SELECT id FROM users WHERE role='CUSTOMER')");
        assertThat(request("GET","/auth/me",null,customer).statusCode()).isEqualTo(401);
        assertThat(request("POST","/auth/register",Map.of("fullName","A","phone","invalid","email","bad","password","short"),null).statusCode()).isEqualTo(400);
        assertThat(request("POST","/auth/register",Map.of("fullName","A","phone","0900000004","email","other@test.invalid","password","đ".repeat(40)),null).statusCode()).isEqualTo(400);
    }
    @Test void recruitmentRequiresReviewAndCreatesExactlyOneStaff() throws Exception {
        long id=candidate(); String path="/admin/candidates/"+id;
        var provision=Map.of("password","StaffTest!123","rank","STANDARD");
        assertThat(request("POST",path+"/staff-account",provision,admin).statusCode()).isEqualTo(409);
        assertThat(request("PATCH",path,Map.of("status","EVALUATION","trainingScore",100),admin).statusCode()).isEqualTo(409);
        assertThat(request("PATCH",path,Map.of("status","ONBOARDING"),admin).statusCode()).isEqualTo(200);
        assertThat(request("PATCH",path,Map.of("status","TRAINING","documentChecklist",List.of("Bản sao CCCD"),"onboardingNote","Thiếu giấy tờ"),admin).statusCode()).isEqualTo(400);
        assertThat(request("PATCH",path,Map.of("status","TRAINING","documentChecklist",List.of("Bản sao CCCD","Hộ khẩu / KT3","Giấy khám sức khỏe"),"onboardingNote","Hợp lệ"),admin).statusCode()).isEqualTo(200);
        assertThat(request("PATCH",path,Map.of("status","EVALUATION","trainingScore",101,"trainingNotes","Đạt"),admin).statusCode()).isEqualTo(400);
        assertThat(request("PATCH",path,Map.of("status","EVALUATION","trainingScore",85,"trainingNotes","Đạt"),admin).statusCode()).isEqualTo(200);
        var futures=List.of(CompletableFuture.supplyAsync(()->provisionRequest(path,provision)),CompletableFuture.supplyAsync(()->provisionRequest(path,provision)));
        assertThat(futures.stream().map(CompletableFuture::join).sorted().toList()).containsExactly(201,409);
        assertThat(db.queryForObject("SELECT count(*) FROM staffs",Integer.class)).isEqualTo(1);
        String staff=token(request("POST","/auth/login",Map.of("identifier","staff@test.invalid","password","StaffTest!123"),null));
        assertThat(request("GET","/auth/profile",null,staff).body()).contains("Ứng viên thử nghiệm","STAFF");
        assertThat(request("GET","/auth/profile",null,staff).body()).contains("1998-06-18","staff-avatar.png");
        assertThat(db.queryForObject("SELECT birth_year FROM staffs",Integer.class)).isEqualTo(1998);
        assertThat(db.queryForObject("SELECT experience_years FROM staffs",Integer.class)).isEqualTo(3);
        assertThat(request("GET","/staff-directory",null,null).body()).contains("1998-06-18","staff-avatar.png");
        assertThat(request("PATCH","/customer/profile",Map.of("fullName","Không được sửa","phone","0900000003"),staff).statusCode()).isEqualTo(403);
        assertThat(request("PATCH","/staff/profile",Map.of("phone","0900000003","bio","Thông tin lưu thật"),staff).statusCode()).isEqualTo(200);
        assertThat(request("GET","/auth/profile",null,staff).body()).contains("Thông tin lưu thật");
        assertThat(request("PATCH","/staff/profile",Map.of("phone","0900000003","bio","Không được sửa"),admin).statusCode()).isEqualTo(403);
        assertThat(request("GET","/admin/customers",null,staff).statusCode()).isEqualTo(403);
        assertThat(request("GET","/admin/staff",null,admin).body()).contains("Ứng viên thử nghiệm");
    }
    int provisionRequest(String path,Object body) {
        try { return request("POST",path+"/staff-account",body,admin).statusCode(); }
        catch(Exception ex) { throw new RuntimeException(ex); }
    }
    @Test void duplicateContactRollsBackProvisionAndLeavesCandidateUnchanged() throws Exception {
        long id=candidate();evaluate(id);
        request("POST","/auth/register",Map.of("fullName","Khách hàng","phone","0900000004","email","staff@test.invalid","password","Customer!123"),null);
        assertThat(request("POST","/admin/candidates/"+id+"/staff-account",Map.of("password","StaffTest!123","rank","INTERN"),admin).statusCode()).isEqualTo(409);
        assertThat(db.queryForObject("SELECT count(*) FROM staffs",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT staff_id FROM staff_candidates WHERE id=?",Long.class,id)).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM users WHERE role='STAFF'",Integer.class)).isZero();
    }
    @Test void staffUpdatesPersistAndDuplicatePhoneRollsBackStatusAndTokenRevocation() throws Exception {
        long candidateId=candidate(); evaluate(candidateId);
        var created=request("POST","/admin/candidates/"+candidateId+"/staff-account",Map.of("password","StaffTest!123","rank","STANDARD"),admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long id=json.readTree(created.body()).get("staffId").asLong();
        String staff=token(request("POST","/auth/login",Map.of("identifier","staff@test.invalid","password","StaffTest!123"),null));
        var duplicate=Map.of("fullName","Tên thay đổi","phone","0900000001","status","LOCKED","statusNote","Thử rollback","rank","PROFESSIONAL");
        assertThat(request("PATCH","/admin/staff/"+id,duplicate,admin).statusCode()).isEqualTo(409);
        assertThat(request("GET","/auth/me",null,staff).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("SELECT account_rank FROM staffs WHERE id=?",String.class,id)).isEqualTo("STANDARD");
        assertThat(request("PATCH","/admin/staff/"+id,Map.of("fullName","Nhân viên đã cập nhật","phone","0900000003","status","LOCKED","statusNote","Thử khóa","rank","PROFESSIONAL"),admin).statusCode()).isEqualTo(204);
        assertThat(request("GET","/auth/me",null,staff).statusCode()).isEqualTo(401);
        assertThat(request("GET","/admin/staff",null,admin).body()).contains("Nhân viên đã cập nhật","PROFESSIONAL","BLOCKED","Thử khóa");
    }

    @Test void publicDirectoryAndModernAddressPersistWithoutDistrict() throws Exception {
        var directory=request("GET","/administrative-units/hcm",null,null);
        assertThat(directory.statusCode()).isEqualTo(200);
        assertThat(directory.body()).contains("Phường Sài Gòn","WARD");
        String customer=token(request("POST","/auth/register",Map.of("fullName","Khách mới","phone","0900000006","email","ward@test.invalid","password","Customer!123"),null));
        var input=Map.of("title","Nhà","recipientName","Khách mới","recipientPhone","0900000006","streetAddress","10 Đường thử nghiệm","ward","Phường Sài Gòn","city","TP. Hồ Chí Minh");
        var created=request("POST","/customer/addresses",input,customer);
        assertThat(created.statusCode()).isEqualTo(201);
        long id=json.readTree(created.body()).get("id").asLong();
        assertThat(db.queryForObject("SELECT district FROM addresses WHERE id=?",String.class,id)).isEmpty();
        assertThat(db.queryForObject("SELECT ward FROM addresses WHERE id=?",String.class,id)).isEqualTo("Phường Sài Gòn");
        assertThat(request("GET","/customer/addresses/"+id,null,customer).body()).contains("10 Đường thử nghiệm, Phường Sài Gòn, TP. Hồ Chí Minh");
        assertThat(request("GET","/customer/addresses/"+id,null,admin).statusCode()).isEqualTo(403);
    }
    @Test void invalidModernWardDoesNotWriteAndLegacyAddressIsRetained() throws Exception {
        String customer=token(request("POST","/auth/register",Map.of("fullName","Khách cũ","phone","0900000006","email","ward@test.invalid","password","Customer!123"),null));
        var input=new LinkedHashMap<String,Object>(Map.of("title","Nhà","recipientName","Khách cũ","recipientPhone","0900000006","streetAddress","10 Đường thử nghiệm","ward","Quận 1","city","TP. Hồ Chí Minh"));
        assertThat(request("POST","/customer/addresses",input,customer).statusCode()).isEqualTo(400);
        assertThat(db.queryForObject("SELECT count(*) FROM addresses",Integer.class)).isZero();
        input.put("district","Quận 1"); input.put("ward","Phường Bến Nghé");
        var legacy=request("POST","/customer/addresses",input,customer);
        assertThat(legacy.statusCode()).isEqualTo(201);
        long id=json.readTree(legacy.body()).get("id").asLong();
        assertThat(request("GET","/customer/addresses/"+id,null,customer).body()).contains("Phường Bến Nghé","Quận 1");
        input.put("district",""); input.put("ward","Phường Sài Gòn");
        assertThat(request("PUT","/customer/addresses/"+id,input,customer).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("SELECT district FROM addresses WHERE id=?",String.class,id)).isEmpty();
    }
}
