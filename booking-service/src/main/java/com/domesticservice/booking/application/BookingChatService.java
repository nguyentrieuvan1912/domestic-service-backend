package com.domesticservice.booking.application;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.domesticservice.booking.application.BookingOperationsService.*;
@Service
public class BookingChatService {
    private final BookingOperationsService ops;private final JdbcTemplate db;private final tools.jackson.databind.ObjectMapper json;
    public BookingChatService(BookingOperationsService ops,JdbcTemplate db,tools.jackson.databind.ObjectMapper json){this.ops=ops;this.db=db;this.json=json;}
    private Map<String,Object>conversation(long user,String role,long id,boolean lock){String own=role.equals("STAFF")?"c.staff_id":"c.customer_id";var rows=db.queryForList("SELECT c.*,b.status AS booking_status,b.booking_code,b.service_snapshot,b.address_snapshot FROM conversations c JOIN bookings b ON b.id=c.booking_id WHERE c.id=? AND "+own+"=?"+(lock?" FOR UPDATE OF c":""),id,user);if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"Không tìm thấy hội thoại của tài khoản này.");return rows.getFirst();}
    public List<Map<String,Object>>list(String token) {
        var user=ops.actor(token,"CUSTOMER","STAFF");
        String own="STAFF".equals(user.get("role"))?"c.staff_id":"c.customer_id";
        var people=new HashMap<Long,Map<String,Object>>();
        for(var person:ops.identityClient().list("/api/v1/identity/staff-directory",token))people.put(ServiceHttpClient.userId(person),person);
        String query="""
            SELECT c.*,b.status AS booking_status,b.booking_code,b.service_snapshot,b.address_snapshot,
                   recent.content AS last_content,recent.sent_at AS last_sent_at,
                   counts.customer_unread,counts.staff_unread
            FROM conversations c JOIN bookings b ON b.id=c.booking_id
            LEFT JOIN LATERAL (SELECT content,sent_at FROM messages WHERE conversation_id=c.id ORDER BY sent_at DESC,id DESC LIMIT 1) recent ON true
            LEFT JOIN LATERAL (SELECT count(*) FILTER (WHERE sender_type='STAFF') AS customer_unread,
                count(*) FILTER (WHERE sender_type='CUSTOMER') AS staff_unread
                FROM messages WHERE conversation_id=c.id AND read_at IS NULL) counts ON true
            WHERE """+" "+own+"=? ORDER BY c.created_at DESC";
        return db.queryForList(query,user.get("id")).stream().map(c->{
            var service=ops.object(c.get("service_snapshot"));var address=ops.object(c.get("address_snapshot"));
            var staff=people.getOrDefault(((Number)c.get("staff_id")).longValue(),Map.of());
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",c.get("id"));item.put("bookingId",c.get("booking_id"));item.put("bookingCode",c.get("booking_code"));item.put("customerId",c.get("customer_id"));item.put("staffId",c.get("staff_id"));
            item.put("customerName",Objects.toString(address.get("recipientName"),Objects.toString(address.get("receiverName"),"Khách hàng #"+c.get("customer_id"))));item.put("customerAvatar","");
            item.put("staffName",Objects.toString(staff.get("fullName"),"Nhân viên #"+c.get("staff_id")));item.put("staffAvatar",Objects.toString(staff.get("avatar"),""));
            item.put("serviceName",Objects.toString(service.get("name"),Objects.toString(service.get("serviceName"),"")));item.put("bookingStatus",c.get("booking_status"));item.put("conversationStatus",c.get("status"));item.put("createdAt",instant(c.get("created_at")));
            item.put("lastMessage",Objects.toString(c.get("last_content"),""));item.put("lastMessageTime",instant(c.get("last_sent_at")==null?c.get("created_at"):c.get("last_sent_at")));
            item.put("unreadCountCustomer",c.get("customer_unread"));item.put("unreadCountStaff",c.get("staff_unread"));return item;
        }).toList();
    }
    public List<Map<String,Object>>messages(String token,long id){var user=ops.actor(token,"CUSTOMER","STAFF");conversation(ServiceHttpClient.userId(user),(String)user.get("role"),id,false);return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_id AS \"senderId\",sender_type AS \"senderType\",message_type AS \"contentType\",content,media_url AS \"mediaUrl\",sent_at AS \"sentAt\",(read_at IS NOT NULL) AS \"isRead\" FROM messages WHERE conversation_id=? ORDER BY sent_at,id",id);}
    @Transactional public Map<String,Object>send(String token,long id,Map<String,Object>b){var user=ops.actor(token,"CUSTOMER","STAFF");var c=conversation(ServiceHttpClient.userId(user),(String)user.get("role"),id,true);String key=text(b,"clientKey",160,true),content=text(b,"content",4000,true);var previous=db.queryForList("SELECT id,content FROM messages WHERE conversation_id=? AND sender_id=? AND client_key=?",id,user.get("id"),key);if(!previous.isEmpty()){if(!content.equals(previous.getFirst().get("content")))throw error(HttpStatus.CONFLICT,"Mã tin nhắn đã được dùng với nội dung khác.");return Map.of("id",previous.getFirst().get("id"));}if(!"ACTIVE".equals(c.get("status"))||List.of("COMPLETED","CANCELLED","REFUNDED","REFUNDING","ABSENT","REJECTED").contains(c.get("booking_status")))throw error(HttpStatus.CONFLICT,"Ca đã kết thúc; hội thoại chỉ được đọc.");long message=ops.insert("INSERT INTO messages(conversation_id,sender_id,sender_type,message_type,content,client_key) VALUES (?,?,?,'TEXT',?,?)",id,user.get("id"),user.get("role"),content,key);long recipient=((Number)c.get("customer_id")).longValue()==ServiceHttpClient.userId(user)?((Number)c.get("staff_id")).longValue():((Number)c.get("customer_id")).longValue();
        var payload=Map.of("messageId",message,"conversationId",id,"recipientId",recipient,"senderId",user.get("id"),"senderName",Objects.toString(user.get("fullName"),"Người gửi"));
        ops.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'CHAT_MESSAGE',CAST(? AS jsonb))",c.get("booking_id"),json.writeValueAsString(payload));
        return Map.of("id",message);}
    @Transactional public Map<String,Object>read(String token,long id,Map<String,Object>body){var user=ops.actor(token,"CUSTOMER","STAFF");conversation(ServiceHttpClient.userId(user),(String)user.get("role"),id,false);long through=body==null||!body.containsKey("throughMessageId")?Long.MAX_VALUE:num(body,"throughMessageId",0,0,Long.MAX_VALUE);int changed=db.update("UPDATE messages SET read_at=now() WHERE conversation_id=? AND sender_id IS DISTINCT FROM ? AND read_at IS NULL AND id<=?",id,user.get("id"),through);long unread=db.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=? AND sender_type=? AND read_at IS NULL",Long.class,id,"CUSTOMER".equals(user.get("role"))?"STAFF":"CUSTOMER");return Map.of("changed",changed,"unreadCount",unread);}
}
