package com.domesticservice.ai.application;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiConversationService {
    private final JdbcTemplate db;
    private final ServiceHttpClient identity;
    private final String providerUrl, providerKey;
    public AiConversationService(JdbcTemplate db, @Value("${clients.identity.base-url}") String identityUrl,
            @Value("${AI_PROVIDER_URL:}") String providerUrl, @Value("${AI_PROVIDER_KEY:}") String providerKey) {
        this.db = db; identity = new ServiceHttpClient(identityUrl); this.providerUrl = providerUrl; this.providerKey = providerKey;
    }
    private long actor(String token) { return ServiceHttpClient.userId(identity.authenticate(token, "CUSTOMER")); }
    private void owned(long user, long id, boolean lock) {
        if (db.queryForList("SELECT id FROM ai_conversations WHERE id=? AND customer_id=?" + (lock ? " FOR UPDATE" : ""), id, user).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại của tài khoản này.");
    }
    public List<Map<String,Object>> conversations(String token) {
        return db.queryForList("SELECT id,title,status,started_at AS \"startedAt\" FROM ai_conversations WHERE customer_id=? AND status='ACTIVE' ORDER BY id DESC", actor(token));
    }
    @Transactional public Map<String,Object> create(String token) {
        long id = db.queryForObject("INSERT INTO ai_conversations(customer_id,title) VALUES (?,'Trợ lý dịch vụ') RETURNING id", Long.class, actor(token));
        return Map.of("id",id);
    }
    public List<Map<String,Object>> messages(String token, long id) {
        owned(actor(token),id,false);
        return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_type AS role,content,created_at AS \"createdAt\" FROM ai_messages WHERE conversation_id=? ORDER BY id",id);
    }
    @Transactional public Map<String,Object> send(String token, long id, Map<String,Object> body) {
        long user = actor(token); owned(user,id,true);
        String text=Objects.toString(body.get("content"),"").strip(), key=Objects.toString(body.get("clientKey"),"");
        if (text.isEmpty() || text.length()>4000 || key.isEmpty() || key.length()>160)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nội dung hoặc mã yêu cầu không hợp lệ.");
        var prior=db.queryForList("SELECT id,content FROM ai_messages WHERE conversation_id=? AND client_key=?",id,key);
        if(!prior.isEmpty()) {
            if(!text.equals(prior.getFirst().get("content"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã yêu cầu đã dùng với nội dung khác.");
            return Map.of("id",prior.getFirst().get("id"));
        }
        if(providerUrl.isBlank() || providerKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trợ lý AI chưa được cấu hình nhà cung cấp. Vui lòng đặt dịch vụ bằng các bước trên giao diện.");
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_conversations WHERE id=? AND status='ACTIVE')",Boolean.class,id)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Hội thoại đã kết thúc.");
        var context=db.queryForList("SELECT sender_type AS role,content FROM ai_messages WHERE conversation_id=? ORDER BY id DESC LIMIT 20",id);
        Collections.reverse(context);context.add(Map.of("role","USER","content",text));
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()); factory.setReadTimeout(Duration.ofSeconds(30));
        String content;
        try {
            var result=RestClient.builder().requestFactory(factory).build().post().uri(providerUrl).header("Authorization","Bearer "+providerKey).body(Map.of("messages",context,"instructions","Tư vấn dịch vụ bằng tiếng Việt. Không tuyên bố đã tạo đơn, thanh toán hay cấp năng lực; khách hàng xác nhận trên luồng đặt dịch vụ. Không suy đoán giá, lịch trống, khuyến mãi hoặc thông tin nhân viên.")).retrieve().body(Map.class);
            content=result==null?"":Objects.toString(result.get("content"),"").strip();
            if(content.isEmpty() || content.length()>20000) throw new IllegalStateException("Invalid provider response");
        } catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa nhận được phản hồi hợp lệ từ nhà cung cấp AI. Vui lòng thử lại."); }
        db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content,client_key) VALUES (?,'USER',?,?) RETURNING id",Long.class,id,text,key);
        long reply=db.queryForObject("INSERT INTO ai_messages(conversation_id,sender_type,content) VALUES (?,'ASSISTANT',?) RETURNING id",Long.class,id,content);
        return Map.of("id",reply);
    }
    @Transactional public void clear(String token,long id) { owned(actor(token),id,true); db.update("UPDATE ai_conversations SET status='ENDED',ended_at=now() WHERE id=?",id); }
}
