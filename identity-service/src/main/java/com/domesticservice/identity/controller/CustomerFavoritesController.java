package com.domesticservice.identity.controller;
import com.domesticservice.identity.application.AccountService;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/identity/customer/favorites")
public class CustomerFavoritesController {
    private final JdbcTemplate db;private final AccountService accounts;private final ServiceHttpClient catalog;
    public CustomerFavoritesController(JdbcTemplate db,AccountService accounts,@Value("${clients.catalog.base-url}")String url){this.db=db;this.accounts=accounts;catalog=new ServiceHttpClient(url);}
    private long customer(String token){var user=accounts.authenticate(token,false);if(!"CUSTOMER".equals(user.role()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ khách hàng sử dụng chức năng này.");return user.id();}
    @GetMapping public List<Long>list(@RequestHeader(value="Authorization",required=false)String token){return db.queryForList("SELECT service_id FROM customer_favorites WHERE customer_id=? ORDER BY created_at DESC",Long.class,customer(token));}
    @PutMapping("/{id}") @Transactional public Map<String,Object>save(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){long user=customer(token);if(!(b.get("favorite") instanceof Boolean selected))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái lưu dịch vụ không hợp lệ.");if(selected){catalog.get("/api/v1/catalog/services/"+id,token);db.update("INSERT INTO customer_favorites(customer_id,service_id) VALUES (?,?) ON CONFLICT DO NOTHING",user,id);}else db.update("DELETE FROM customer_favorites WHERE customer_id=? AND service_id=?",user,id);return Map.of("serviceId",id,"favorite",selected);}
}
