package com.domesticservice.identity.controller;
import com.domesticservice.identity.application.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@RestController @RequestMapping("/api/v1/identity")
public class IdentityOperationsController {
    private final AccountService accounts;private final JdbcTemplate db;private final ObjectMapper json;private final String serviceKey;
    public IdentityOperationsController(AccountService accounts,JdbcTemplate db,ObjectMapper json,@Value("${INTERNAL_SERVICE_TOKEN:}")String key){this.accounts=accounts;this.db=db;this.json=json;serviceKey=key;}
    @PostMapping("/internal/events") @Transactional public Map<String,Object>event(@RequestHeader(value="X-Service-Key",required=false)String key,@RequestBody Map<String,Object>b){
        if(serviceKey.isBlank()||key==null||!MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền gửi sự kiện nội bộ.");
        String event=Objects.toString(b.get("eventKey"),""),type=Objects.toString(b.get("type"),"SYSTEM"),title=Objects.toString(b.get("title"),""),content=Objects.toString(b.get("content"),"");
        if(event.isBlank()||event.length()>200||type.length()>50||title.isBlank()||title.length()>255||content.isBlank()||content.length()>5000||!(b.get("userIds") instanceof List<?> ids)||ids.size()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sự kiện không hợp lệ.");
        int changed=0;for(Object raw:ids){long id;try{id=Long.parseLong(raw.toString());}catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ID người nhận không hợp lệ.");}var person=db.queryForList("SELECT id,role FROM users WHERE id=?",id);if(person.isEmpty())continue;int inserted=db.update("INSERT INTO notifications(user_id,notification_type,title,content,reference_id,payload,event_key) VALUES (?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT(user_id,event_key) WHERE event_key IS NOT NULL DO NOTHING",id,type,title,content,b.get("referenceId"),json.writeValueAsString(b.getOrDefault("data",Map.of())),event);changed+=inserted;if(inserted>0&&Boolean.TRUE.equals(b.get("bookingCreated"))&&"CUSTOMER".equals(person.getFirst().get("role")))db.update("UPDATE customers SET total_bookings=total_bookings+1 WHERE id=?",id);}
        return Map.of("created",changed);
    }
    @GetMapping("/admin/support-requests")public List<Map<String,Object>>support(@RequestHeader(value="Authorization",required=false)String token){accounts.authenticate(token,true);return db.queryForList("SELECT id,sender_name AS name,email,subject,message,status,admin_note AS \"adminNote\",created_at AS \"createdAt\" FROM support_requests ORDER BY id DESC LIMIT 500");}
    @PatchMapping("/admin/support-requests/{id}") @Transactional public Map<String,Object>support(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){accounts.authenticate(token,true);String state=Objects.toString(b.get("status"),""),note=Objects.toString(b.get("adminNote"),"");if(!List.of("RECEIVED","IN_PROGRESS","RESOLVED").contains(state)||note.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nội dung xử lý không hợp lệ.");if(db.update("UPDATE support_requests SET status=?,admin_note=?,updated_at=now() WHERE id=?",state,note,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu hỗ trợ.");return Map.of("id",id,"status",state);}
}
