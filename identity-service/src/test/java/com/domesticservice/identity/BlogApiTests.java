package com.domesticservice.identity;

import com.domesticservice.identity.service.*;
import com.domesticservice.identity.dto.AccountRequests.Login;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:blogtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class BlogApiTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired AccountService accounts;
    @MockitoBean CloudinaryClient cloud;
    String token;
    @BeforeEach void schema() throws Exception {
        try(var connection=Objects.requireNonNull(db.getDataSource()).getConnection()) {
            for(String script:List.of("accounts-schema.sql","db/migration/V19__blog_studio.sql","db/migration/V20__publish_existing_blog_articles.sql"))
                ScriptUtils.executeSqlScript(connection,new ClassPathResource(script));
        }
        accounts.bootstrap("editor@test.invalid","0901000001","EditorTest!123");
        token="Bearer "+accounts.login(new Login("editor@test.invalid","EditorTest!123")).accessToken();
        when(cloud.ready()).thenReturn(false);
    }
    HttpResponse<String> call(String method,String path,Object body,String auth) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/identity"+path)).header("Content-Type","application/json");
        if(auth!=null)request.header("Authorization",auth);
        return HttpClient.newHttpClient().send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,Object> read(HttpResponse<String> response) {return json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});}
    Map<String,Object> draft(String slug,String status) {
        var body=new LinkedHashMap<String,Object>();
        body.putAll(Map.of("title","Bài viết kiểm thử","slug",slug,"status",status,"excerpt","Mô tả bài viết","category","CLEANING_TIPS","coverUrl","https://images.example.org/cover.jpg","authorName","Biên tập viên"));
        body.put("document",Map.of("type","doc","content",List.of(Map.of("type","paragraph","attrs",Map.of("textAlign","center"),"content",List.of(Map.of("type","text","text","Nội dung thật được lưu trong database kiểm thử.","marks",List.of(Map.of("type","textStyle","attrs",Map.of("fontFamily","Georgia","fontSize","24px","color","#065f46")))))))));
        return body;
    }
    @Test void publicArticlesHaveFullContentButDraftsAndAdminEndpointsArePrivate() throws Exception {
        assertEquals(4,json.readTree(call("GET","/blog",null,null).body()).size());
        var article=read(call("GET","/blog/giu-nha-cua-gon-gang-moi-ngay",null,null));
        assertTrue(json.writeValueAsString(article.get("document")).length()>1000);
        assertEquals(401,call("GET","/admin/blog",null,null).statusCode());
        assertEquals(401,call("POST","/admin/blog",draft("private-draft","DRAFT"),null).statusCode());
        assertEquals(201,call("POST","/admin/blog",draft("private-draft","DRAFT"),token).statusCode());
        assertEquals(404,call("GET","/blog/private-draft",null,null).statusCode());
    }
    @Test void publishingPersistsFormattingThenArchivingHidesTheArticle() throws Exception {
        var create=call("POST","/admin/blog",draft("publish-cycle","DRAFT"),token);assertEquals(201,create.statusCode(),create.body());
        var article=read(create);String path="/admin/blog/"+article.get("id");article.put("status","PUBLISHED");
        var published=call("PUT",path,article,token);assertEquals(200,published.statusCode(),published.body());
        var publicArticle=read(call("GET","/blog/publish-cycle",null,null));
        assertEquals("Georgia",json.readTree(json.writeValueAsString(publicArticle.get("document"))).at("/content/0/content/0/marks/0/attrs/fontFamily").asText());
        var hide=read(published);hide.put("status","ARCHIVED");assertEquals(200,call("PUT",path,hide,token).statusCode());
        assertEquals(404,call("GET","/blog/publish-cycle",null,null).statusCode());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM blog_articles WHERE slug='publish-cycle' AND status='ARCHIVED'",Integer.class));
    }
    @Test void duplicateSlugsAndStaleEditsCannotOverwriteSavedData() throws Exception {
        var body=draft("one-article","DRAFT");var first=read(call("POST","/admin/blog",body,token));
        assertEquals(409,call("POST","/admin/blog",body,token).statusCode());
        String path="/admin/blog/"+first.get("id");first.put("title","Phiên bản mới");assertEquals(200,call("PUT",path,first,token).statusCode());
        first.put("title","Phiên bản cũ ghi đè");assertEquals(409,call("PUT",path,first,token).statusCode());
        assertEquals("Phiên bản mới",db.queryForObject("SELECT title FROM blog_articles WHERE slug='one-article'",String.class));
    }
    @Test void scriptsUnsafeLinksAndUnsupportedFormattingAreRejected() throws Exception {
        var body=draft("unsafe","PUBLISHED");
        body.put("document",Map.of("type","doc","content",List.of(Map.of("type","script","text","alert(1)"))));
        assertEquals(400,call("POST","/admin/blog",body,token).statusCode());
        body=draft("unsafe","PUBLISHED");body.put("coverUrl","javascript:alert(1)");
        assertEquals(400,call("POST","/admin/blog",body,token).statusCode());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->BlogDocumentValidator.validate(Map.of("type","doc","content",List.of(Map.of("type","paragraph","attrs",Map.of("textAlign","expression(alert(1))"))))));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM blog_articles WHERE slug='unsafe'",Integer.class));
    }
    @Test void publicationRequiresCoverSummaryAndContent() throws Exception {
        var body=draft("incomplete","PUBLISHED");body.put("coverUrl","");
        assertEquals(400,call("POST","/admin/blog",body,token).statusCode());
        body.put("status","DRAFT");assertEquals(201,call("POST","/admin/blog",body,token).statusCode());
    }
    @Test void mediaRequiresAdminOwnershipAndCloudVerificationBeforeUse() throws Exception {
        assertEquals(401,call("POST","/admin/blog/media/signature",Map.of("type","image"),null).statusCode());
        assertEquals(404,call("POST","/admin/blog/media/123/complete",null,token).statusCode());
        var capabilities=read(call("GET","/admin/blog/media/capabilities",null,token));assertEquals(false,capabilities.get("enabled"));
        long admin=db.queryForObject("SELECT id FROM users WHERE role='ADMIN'",Long.class);
        db.update("INSERT INTO blog_media(owner_id,public_id,resource_type,expires_at) VALUES (?,'test/blog/image','image',?)",admin,java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(60)));
        long id=db.queryForObject("SELECT id FROM blog_media",Long.class);
        when(cloud.inspect("test/blog/image","image")).thenReturn(Map.of("secure_url","https://res.cloudinary.com/test/image/upload/blog.jpg"));
        assertEquals(200,call("POST","/admin/blog/media/"+id+"/complete",null,token).statusCode());
        assertEquals("READY",db.queryForObject("SELECT status FROM blog_media WHERE id=?",String.class,id));
    }
    @Test void customerCannotUseTheAdminStudioOrAnotherOwnersMedia() throws Exception {
        String password=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("CustomerTest!123");
        db.update("INSERT INTO users(user_name,password_hash,full_name,phone,email,role,status) VALUES ('blog-customer',?,'Customer','0901000002','customer@test.invalid','CUSTOMER','ACTIVE')",password);
        long customer=db.queryForObject("SELECT id FROM users WHERE user_name='blog-customer'",Long.class);
        db.update("INSERT INTO customers(id) VALUES (?)",customer);
        String customerToken="Bearer "+accounts.login(new Login("customer@test.invalid","CustomerTest!123")).accessToken();
        assertEquals(403,call("GET","/admin/blog",null,customerToken).statusCode());
        assertEquals(403,call("POST","/admin/blog",draft("not-admin","DRAFT"),customerToken).statusCode());
        db.update("INSERT INTO blog_media(owner_id,public_id,resource_type,expires_at) VALUES (?,'other-owner','image',?)",customer,java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(60)));
        long id=db.queryForObject("SELECT id FROM blog_media",Long.class);
        assertEquals(404,call("POST","/admin/blog/media/"+id+"/complete",null,token).statusCode());
        verify(cloud,never()).inspect("other-owner","image");
    }
}
