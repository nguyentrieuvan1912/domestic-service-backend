package com.domesticservice.identity.application;

import com.domesticservice.identity.application.AccountService.User;
import com.domesticservice.identity.dto.CustomerRequests.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static com.domesticservice.identity.application.AccountService.error;

@Service
@Transactional
public class CustomerDataService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CustomerDataService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    private void customer(User user) {
        if (!"CUSTOMER".equals(user.role())) throw error(HttpStatus.FORBIDDEN,"Vui lòng sử dụng tài khoản khách hàng.");
    }
    private void lockCustomer(User user) {
        customer(user);
        db.queryForObject("SELECT id FROM customers WHERE id=? FOR UPDATE",Long.class,user.id());
    }
    private Map<String,Object> address(ResultSet rs,int row) throws SQLException {
        var value=new LinkedHashMap<String,Object>();
        value.put("id",rs.getLong("id")); value.put("customerId",rs.getLong("customer_id"));
        value.put("title",rs.getString("title")); value.put("recipientName",rs.getString("receiver_name"));
        value.put("recipientPhone",rs.getString("receiver_phone")); value.put("streetAddress",rs.getString("detail_address"));
        value.put("ward",rs.getString("ward")); value.put("district",rs.getString("district")); value.put("city",rs.getString("province"));
        value.put("fullAddress",String.join(", ",List.of(rs.getString("detail_address"),rs.getString("ward"),rs.getString("district"),rs.getString("province")).stream().filter(s->!s.isBlank()).toList()));
        value.put("latitude",rs.getBigDecimal("latitude")); value.put("longitude",rs.getBigDecimal("longitude"));
        value.put("note",rs.getString("note")); value.put("isDefault",rs.getBoolean("is_default"));
        return value;
    }
    public List<Map<String,Object>> addresses(User user) {
        customer(user); return db.query("SELECT * FROM addresses WHERE customer_id=? ORDER BY is_default DESC,id",this::address,user.id());
    }
    public Map<String,Object> address(User user,long id) {
        customer(user);
        var values=db.query("SELECT * FROM addresses WHERE id=? AND customer_id=?",this::address,id,user.id());
        if (values.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy địa chỉ của bạn.");
        return values.getFirst();
    }
    public Map<String,Object> saveAddress(User user,Long id,Address body) {
        lockCustomer(user);
        var existing=id==null?null:address(user,id);
        String district=Objects.toString(body.district(),"").strip();
        String ward=java.text.Normalizer.normalize(Objects.toString(body.ward(),"").strip(),java.text.Normalizer.Form.NFC);
        String province=body.city().strip();
        if(district.isBlank()) {
            if(ward.isBlank()) throw error(HttpStatus.BAD_REQUEST,"Vui lòng chọn phường/xã.");
            if(!List.of("TP. Hồ Chí Minh","Thành phố Hồ Chí Minh","Hồ Chí Minh").contains(province)) throw error(HttpStatus.BAD_REQUEST,"Danh mục phường/xã hiện hỗ trợ TP. Hồ Chí Minh.");
            province="TP. Hồ Chí Minh";
            if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM administrative_units WHERE province=? AND name=?)",Boolean.class,province,ward))) throw error(HttpStatus.BAD_REQUEST,"Vui lòng chọn phường/xã hợp lệ trong danh sách TP. Hồ Chí Minh.");
        }
        final String savedProvince=province;
        boolean isDefault=Boolean.TRUE.equals(body.isDefault()) || (existing!=null && Boolean.TRUE.equals(existing.get("isDefault")))
            || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM addresses WHERE customer_id=? AND is_default=true)",Boolean.class,user.id()));
        if (isDefault) db.update("UPDATE addresses SET is_default=false,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_default=true",user.id());
        String phone=body.recipientPhone().startsWith("+84")?"0"+body.recipientPhone().substring(3):body.recipientPhone();
        if (id==null) {
            var key=new GeneratedKeyHolder();
            db.update(connection->{
                var statement=connection.prepareStatement("INSERT INTO addresses(customer_id,title,receiver_name,receiver_phone,province,district,ward,detail_address,note,is_default) VALUES (?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});
                Object[] values={user.id(),body.title().strip(),body.recipientName().strip(),phone,savedProvince,district,ward,body.streetAddress().strip(),Objects.toString(body.note(),"").strip(),isDefault};
                for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
                return statement;
            },key);
            id=Objects.requireNonNull(key.getKey()).longValue();
        } else db.update("UPDATE addresses SET title=?,receiver_name=?,receiver_phone=?,province=?,district=?,ward=?,detail_address=?,note=?,is_default=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=?",
            body.title().strip(),body.recipientName().strip(),phone,savedProvince,district,ward,body.streetAddress().strip(),Objects.toString(body.note(),"").strip(),isDefault,id,user.id());
        return address(user,id);
    }
    public Map<String,Object> setDefault(User user,long id) {
        lockCustomer(user); address(user,id);
        db.update("UPDATE addresses SET is_default=false,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_default=true",user.id());
        db.update("UPDATE addresses SET is_default=true,updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=?",id,user.id());
        return address(user,id);
    }
    public void deleteAddress(User user,long id) {
        lockCustomer(user); address(user,id);
        db.update("DELETE FROM addresses WHERE id=? AND customer_id=?",id,user.id());
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM addresses WHERE customer_id=? AND is_default=true)",Boolean.class,user.id()))) {
            var next=db.queryForList("SELECT id FROM addresses WHERE customer_id=? ORDER BY id LIMIT 1",Long.class,user.id());
            if (!next.isEmpty()) db.update("UPDATE addresses SET is_default=true,updated_at=CURRENT_TIMESTAMP WHERE id=?",next.getFirst());
        }
    }
    private Map<String,Object> notification(ResultSet rs,int row,User user) throws SQLException {
        var value=new LinkedHashMap<String,Object>();
        value.put("id",rs.getLong("id")); value.put("userId",user.id()); value.put("targetRole",user.role());
        value.put("type",rs.getString("notification_type")); value.put("title",rs.getString("title")); value.put("content",rs.getString("content"));
        value.put("referenceId",rs.getObject("reference_id"));
        value.put("data",json.readValue(rs.getString("payload"),new TypeReference<Map<String,Object>>() {}));
        value.put("isRead",rs.getTimestamp("read_at")!=null); value.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());
        return value;
    }
    public List<Map<String,Object>> notifications(User user) {
        return db.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC",(rs,row)->notification(rs,row,user),user.id());
    }
    public Map<String,Object> notification(User user,long id) {
        var values=db.query("SELECT * FROM notifications WHERE id=? AND user_id=?",(rs,row)->notification(rs,row,user),id,user.id());
        if(values.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo của bạn.");
        return values.getFirst();
    }
    public Map<String,Integer> markRead(User user,Long id) {
        if(id!=null) notification(user,id);
        int changed=id==null?db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE user_id=? AND read_at IS NULL",user.id())
            :db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND read_at IS NULL",id,user.id());
        return Map.of("changed",changed);
    }
    public Map<String,Object> contact(Contact body) {
        var key=new GeneratedKeyHolder();
        db.update(connection->{
            var statement=connection.prepareStatement("INSERT INTO support_requests(sender_name,email,subject,message) VALUES (?,?,?,?)",new String[]{"id"});
            statement.setString(1,body.name().strip()); statement.setString(2,body.email().strip().toLowerCase(Locale.ROOT));
            statement.setString(3,body.subject().strip()); statement.setString(4,body.message().strip()); return statement;
        },key);
        return Map.of("id",Objects.requireNonNull(key.getKey()).longValue(),"status","RECEIVED");
    }
}
