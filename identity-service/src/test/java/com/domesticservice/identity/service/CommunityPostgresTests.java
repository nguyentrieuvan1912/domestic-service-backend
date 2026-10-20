package com.domesticservice.identity.service;

import com.domesticservice.identity.config.CloudinaryProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL/Flyway, sessions, visibility, ownership and row locks. Only Cloudinary HTTP is isolated. */
@EnabledIfEnvironmentVariable(named="COMMUNITY_POSTGRES_VERIFY",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${COMMUNITY_TEST_URL}","spring.datasource.username=postgres","spring.datasource.password=community_test_only",
    "spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","spring.profiles.active=community-verification",
    "identity.bootstrap.email=","identity.bootstrap.phone=","identity.bootstrap.password="})
@Import(CommunityPostgresTests.Config.class)
class CommunityPostgresTests {
    @TestConfiguration(proxyBeanMethods=false) static class Config {
        @Bean(destroyMethod="stop") Peer cloudinaryPeer(ObjectMapper json) throws Exception{return new Peer(json);}
        @Bean @Primary CloudinaryClient verifiedCloud(Peer peer,ObjectMapper json){return new CloudinaryClient(new CloudinaryProperties("test-cloud","test-api","test-secret"),json,HttpClient.newHttpClient(),peer.url());}
    }
    static class Peer {
        final HttpServer server;volatile long bytes=100;volatile double duration=1;
        Peer(ObjectMapper json)throws Exception{
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath(),type=path.contains("/video/")?"video":"image",prefix="/v1_1/test-cloud/resources/"+type+"/upload/",id=path.substring(prefix.length());
                Map<String,Object> asset=Map.of("public_id",id,"resource_type",type,"type","upload","bytes",bytes,"width",100,"height",100,"duration",type.equals("video")?duration:0,"format",type.equals("image")?"jpg":"mp4","secure_url","https://res.cloudinary.com/test-cloud/"+type+"/upload/v1/"+id+"."+(type.equals("image")?"jpg":"mp4"));
                byte[] body=json.writeValueAsBytes(asset);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });server.start();
        }
        String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        public void stop(){server.stop(0);}
    }
    @Autowired JdbcTemplate db;@Autowired CommunityService service;@Autowired CommunityMediaService media;@Autowired Peer peer;
    @Autowired CommunityReportService reports;@Autowired AccountService accounts;
    String one,two,admin;long oneId;
    @BeforeEach void setup() throws Exception{oneId=account("STAFF");one=session(oneId);two=session(account("CUSTOMER"));admin=session(account("ADMIN"));peer.bytes=100;peer.duration=1;}
    long account(String role){String unique=UUID.randomUUID().toString();return db.queryForObject("INSERT INTO users(user_name,password_hash,full_name,phone,email,role,status) VALUES (?,?,'Community verification',?,?,?,'ACTIVE') RETURNING id",Long.class,unique,"disabled-test-password",unique.substring(0,20),unique+"@test.invalid",role);}
    String session(long id)throws Exception{String token=UUID.randomUUID().toString();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));db.update("INSERT INTO account_sessions(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 hour')",id,hash);return "Bearer "+token;}
    long create(String owner){return ((Number)service.create(owner,Map.of("content","A real persisted test post","requestKey",UUID.randomUUID().toString())).get("id")).longValue();}
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> page){return (List<Map<String,Object>>)page.get("items");}
    @Test void moderationAndThreadBoundariesAreEnforced(){
        long post=create(one),other=create(two);
        assertThrows(ResponseStatusException.class,()->service.post(two,post));assertThrows(ResponseStatusException.class,()->service.comment(two,post,Map.of("content","Not visible")));
        service.moderate(admin,post,Map.of("status","PUBLISHED"));
        long parent=((Number)service.comment(one,post,Map.of("content","Parent","requestKey","parent")).get("id")).longValue();
        var body=Map.<String,Object>of("content","Reply","parentId",parent,"requestKey","reply");
        var reply=service.comment(two,post,body);assertEquals(reply,service.comment(two,post,body));
        var comments=rows(service.comments(two,post,null,null));assertEquals(2,comments.size());assertEquals(parent,((Number)comments.getLast().get("parentId")).longValue());assertEquals("Parent",comments.getLast().get("parentContent"));
        assertThrows(ResponseStatusException.class,()->service.comment(two,other,Map.of("content","Wrong post","parentId",parent)));
        assertThrows(ResponseStatusException.class,()->service.comment(two,post,Map.of("content","Changed","parentId",parent,"requestKey","reply")));
        service.moderate(admin,post,Map.of("status","HIDDEN"));assertThrows(ResponseStatusException.class,()->service.comments(two,post,null,null));
        assertEquals(2,rows(service.comments(one,post,null,null)).size());
    }
    @Test void reactionsReplaceRatherThanMultiplyAndCanBeRemoved() throws Exception{
        long post=create(one);service.moderate(admin,post,Map.of("status","PUBLISHED"));
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->service.react(two,post,Map.of("reaction","LOVE")));var b=pool.submit(()->service.react(two,post,Map.of("reaction","LOVE")));a.get();b.get();
        }
        assertEquals(1L,((Number)service.post(two,post).get("likes")).longValue());
        service.react(two,post,Map.of("reaction","SUPPORT"));assertEquals("SUPPORT",service.post(two,post).get("myReaction"));
        var remove=new HashMap<String,Object>();remove.put("reaction",null);service.react(two,post,remove);assertEquals(0L,((Number)service.post(two,post).get("likes")).longValue());
        long comment=((Number)service.comment(one,post,Map.of("content","A comment")).get("id")).longValue();
        var updated=service.commentReaction(two,post,comment,Map.of("reaction","LIKE"));assertEquals("LIKE",updated.get("myReaction"));
        assertThrows(ResponseStatusException.class,()->service.commentReaction(two,create(two),comment,Map.of("reaction","LIKE")));
        assertThrows(ResponseStatusException.class,()->service.react(two,post,Map.of("reaction","INVALID")));
    }
    @Test void mediaMustBeVerifiedOwnedAndAttachedOnlyOnce(){
        long upload=((Number)media.sign(one,Map.of("type","image")).get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->media.complete(two,upload));
        assertThrows(ResponseStatusException.class,()->service.create(one,Map.of("mediaIds",List.of(upload))));
        var asset=media.complete(one,upload);assertTrue(asset.get("uri").toString().startsWith("https://res.cloudinary.com/test-cloud/"));
        assertThrows(ResponseStatusException.class,()->service.create(two,Map.of("mediaIds",List.of(upload))));
        var body=Map.<String,Object>of("mediaIds",List.of(upload),"requestKey","media-post");
        var post=service.create(one,body);assertEquals(post,service.create(one,body));
        assertThrows(ResponseStatusException.class,()->service.create(one,Map.of("mediaIds",List.of(upload))));
        assertEquals(1,((List<?>)service.post(one,((Number)post.get("id")).longValue()).get("media")).size());
        assertThrows(ResponseStatusException.class,()->service.create(one,Map.of("content","No file URLs","media",List.of(Map.of("uri","file:///local.jpg")))));
    }
    @Test void sizeAndDurationAreVerifiedAgainstProviderMetadata(){
        long image=((Number)media.sign(one,Map.of("type","image")).get("id")).longValue();peer.bytes=11*1024*1024L;assertThrows(ResponseStatusException.class,()->media.complete(one,image));
        peer.bytes=100;long video=((Number)media.sign(one,Map.of("type","video")).get("id")).longValue();peer.duration=121;assertThrows(ResponseStatusException.class,()->media.complete(one,video));
        peer.duration=1;assertEquals("video",media.complete(one,video).get("type"));
    }
    @Test void paginationPreservesRepliesAndNewMessages(){
        long post=create(one);
        for(int i=0;i<55;i++)service.comment(one,post,Map.of("content","Message "+i));
        var latest=service.comments(one,post,null,null);assertEquals(50,rows(latest).size());assertEquals(true,latest.get("hasMore"));
        var older=service.comments(one,post,((Number)latest.get("nextBefore")).longValue(),null);assertEquals(5,rows(older).size());
        long last=((Number)rows(latest).getLast().get("id")).longValue();service.comment(one,post,Map.of("content","New message"));assertEquals(1,rows(service.comments(one,post,null,last)).size());
    }
    long asset(String token,String type){long id=((Number)media.sign(token,Map.of("type",type)).get("id")).longValue();media.complete(token,id);return id;}
    @Test void commentMediaAreOwnedVerifiedAndIdempotent()throws Exception{
        long post=create(one),image=asset(one,"image"),video=asset(one,"video");
        service.moderate(admin,post,Map.of("status","PUBLISHED"));
        assertThrows(ResponseStatusException.class,()->service.comment(two,post,Map.of("mediaIds",List.of(image))));
        var body=Map.<String,Object>of("mediaIds",List.of(image,video),"requestKey","comment-with-media");
        Map<String,Object> saved;
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->service.comment(one,post,body));var second=pool.submit(()->service.comment(one,post,body));
            saved=first.get();assertEquals(saved,second.get());
        }
        assertEquals(2,((List<?>)saved.get("media")).size());
        assertEquals(2L,db.queryForObject("SELECT count(*) FROM community_media WHERE comment_id=? AND status='ATTACHED'",Long.class,saved.get("id")));
        assertThrows(ResponseStatusException.class,()->service.create(one,Map.of("mediaIds",List.of(image))));
        assertThrows(ResponseStatusException.class,()->service.comment(one,post,Map.of("mediaIds",List.of(video))));
        assertThrows(ResponseStatusException.class,()->service.comment(one,post,Map.of("content","changed","requestKey","comment-with-media")));
        long unverified=((Number)media.sign(one,Map.of("type","image")).get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->service.comment(one,post,Map.of("mediaIds",List.of(unverified))));
        assertThrows(ResponseStatusException.class,()->service.comment(one,post,Map.of("content","","mediaIds",List.of())));
    }
    @Test void reportsRequireVisibilityAndAreDeduplicated()throws Exception{
        long post=create(one);long comment=((Number)service.comment(one,post,Map.of("content","Reported comment")).get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->reports.report(two,post,comment,Map.of("reason","SPAM")));
        assertThrows(ResponseStatusException.class,()->reports.report(null,post,comment,Map.of("reason","SPAM")));
        service.moderate(admin,post,Map.of("status","PUBLISHED"));
        assertThrows(ResponseStatusException.class,()->reports.report(two,create(two),comment,Map.of("reason","SPAM")));
        assertThrows(ResponseStatusException.class,()->reports.report(two,post,comment,Map.of("reason","OTHER")));
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->reports.report(two,post,comment,Map.of("reason","SPAM")));var second=pool.submit(()->reports.report(two,post,comment,Map.of("reason","SPAM")));
            assertEquals(first.get(),second.get());
        }
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM community_comment_reports WHERE comment_id=?",Long.class,comment));
        long report=db.queryForObject("SELECT id FROM community_comment_reports WHERE comment_id=?",Long.class,comment);
        assertThrows(ResponseStatusException.class,()->reports.list(two,"OPEN",null));
        assertThrows(ResponseStatusException.class,()->reports.resolve(two,report,Map.of("action","DELETE")));
        reports.resolve(admin,report,Map.of("action","DISMISS","note","Not a violation"));
        assertEquals("DISMISSED",db.queryForObject("SELECT status FROM community_comment_reports WHERE id=?",String.class,report));
        assertFalse((Boolean)rows(service.comments(two,post,null,null)).getFirst().get("deleted"));
    }
    @Test void deleteKeepsEvidenceAndReplyContextWithoutLeakingRemovedContent(){
        long post=create(one),image=asset(one,"image");service.moderate(admin,post,Map.of("status","PUBLISHED"));
        var comment=service.comment(one,post,Map.of("content","Spam evidence","mediaIds",List.of(image)));long id=((Number)comment.get("id")).longValue();
        service.comment(two,post,Map.of("content","A legitimate reply","parentId",id));
        long report=((Number)reports.report(two,post,id,Map.of("reason","MISINFORMATION","detail","Please check")).get("id")).longValue();
        reports.report(one,post,id,Map.of("reason","SPAM"));
        reports.resolve(admin,report,Map.of("action","DELETE","note","Removed spam"));
        assertEquals(reports.resolve(admin,report,Map.of("action","DELETE")),Map.of("id",report,"status","RESOLVED"));
        var comments=rows(service.comments(two,post,null,null));assertEquals(2,comments.size());assertEquals(true,comments.getFirst().get("deleted"));
        assertEquals(List.of(),comments.getFirst().get("media"));assertFalse(comments.getLast().get("parentContent").toString().contains("Spam evidence"));
        assertTrue(((List<?>)service.comments(two,post,null,((Number)comments.getLast().get("id")).longValue()).get("deletedIds")).contains(id));
        assertEquals(1L,((Number)service.post(two,post).get("commentCount")).longValue());
        assertThrows(ResponseStatusException.class,()->service.commentReaction(two,post,id,Map.of("reaction","LIKE")));
        assertThrows(ResponseStatusException.class,()->service.comment(two,post,Map.of("content","reply to deleted","parentId",id)));
        assertThrows(ResponseStatusException.class,()->reports.report(two,post,id,Map.of("reason","SPAM")));
        var evidence=rows(reports.list(admin,"RESOLVED",null)).stream().filter(r->((Number)r.get("id")).longValue()==report).findFirst().orElseThrow();
        assertEquals("Spam evidence",evidence.get("content"));assertEquals(1,((List<?>)evidence.get("media")).size());
        assertEquals(2L,db.queryForObject("SELECT count(*) FROM community_comment_reports WHERE comment_id=? AND status='RESOLVED'",Long.class,id));
    }
    @Test void blockRevokesSessionsAndNeverAllowsBlockingAdmins()throws Exception{
        long post=create(one);service.moderate(admin,post,Map.of("status","PUBLISHED"));
        long comment=((Number)service.comment(one,post,Map.of("content","Abusive" )).get("id")).longValue();
        String secondSession=session(oneId);
        long report=((Number)reports.report(two,post,comment,Map.of("reason","ABUSE")).get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->reports.resolve(admin,report,Map.of("action","BLOCK")));
        reports.resolve(admin,report,Map.of("action","DELETE_AND_BLOCK","note","Repeated abusive comments"));
        assertEquals("BLOCKED",db.queryForObject("SELECT status FROM users WHERE id=?",String.class,oneId));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM account_sessions WHERE user_id=?",Long.class,oneId));
        assertThrows(ResponseStatusException.class,()->accounts.authenticate(one,false));assertThrows(ResponseStatusException.class,()->accounts.authenticate(secondSession,false));
        assertThrows(ResponseStatusException.class,()->service.comment(one,post,Map.of("content","Blocked attempt")));
        long adminComment=((Number)service.comment(admin,post,Map.of("content","Admin content")).get("id")).longValue();
        long protectedReport=((Number)reports.report(two,post,adminComment,Map.of("reason","SPAM")).get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->reports.resolve(admin,protectedReport,Map.of("action","BLOCK","note","Must not work")));
        assertNotNull(accounts.authenticate(admin,true));
    }
}
