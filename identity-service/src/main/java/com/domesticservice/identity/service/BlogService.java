package com.domesticservice.identity.service;

import com.domesticservice.identity.repository.BlogRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static com.domesticservice.identity.service.AccountService.error;

@Service
public class BlogService {
    private final BlogRepository repository;private final AccountService accounts;private final CloudinaryClient cloud;private final ObjectMapper json;
    public BlogService(BlogRepository repository,AccountService accounts,CloudinaryClient cloud,ObjectMapper json) {this.repository=repository;this.accounts=accounts;this.cloud=cloud;this.json=json;}
    public List<Map<String,Object>> list(String token,boolean admin) {
        if(admin) accounts.authenticate(token,true);
        return repository.list(admin).stream().map(this::view).toList();
    }
    public Map<String,Object> published(String slug) {return view(require(repository.published(slug)));}
    public Map<String,Object> get(String token,long id) {accounts.authenticate(token,true);return view(require(repository.find(id)));}
    private Map<String,Object> require(Map<String,Object> row) {if(row==null) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy bài viết.");return row;}
    private String text(Map<String,Object> body,String key,int max,boolean required) {
        if(body.get(key)!=null && !(body.get(key) instanceof String)) throw error(HttpStatus.BAD_REQUEST,"Thông tin bài viết không hợp lệ.");
        String value=Objects.toString(body.get(key),"").strip();
        if(value.length()>max || required && value.isBlank()) throw error(HttpStatus.BAD_REQUEST,"Vui lòng kiểm tra "+key+".");return value;
    }
    public Map<String,Object> save(String token,Long id,Map<String,Object> body) {
        var admin=accounts.authenticate(token,true);var values=new LinkedHashMap<String,Object>();
        for(String key:List.of("slug","title","excerpt","category","coverUrl","authorName","status"))
            values.put(key,text(body,key,key.equals("coverUrl")?2000:key.equals("excerpt")?800:key.equals("title")?240:key.equals("authorName")?160:180,List.of("slug","title","category","status").contains(key)));
        String slug=values.get("slug").toString(),status=values.get("status").toString();
        if(!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status)
                || !Set.of("CLEANING_TIPS","FAMILY_KNOWLEDGE").contains(values.get("category"))) throw error(HttpStatus.BAD_REQUEST,"Đường dẫn, chuyên mục hoặc trạng thái không hợp lệ.");
        if(values.get("authorName").toString().isBlank()) values.put("authorName",admin.fullName());
        String cover=values.get("coverUrl").toString();if(!cover.isBlank()) BlogDocumentValidator.url(cover,false);
        Object document=body.get("document");BlogDocumentValidator.validate(document);
        String serialized=json.writeValueAsString(document);
        if(serialized.length()>250000) throw error(HttpStatus.BAD_REQUEST,"Bài viết quá dài (tối đa 250.000 ký tự dữ liệu).");
        if(status.equals("PUBLISHED") && (cover.isBlank() || values.get("excerpt").toString().isBlank() || !hasContent(document)))
            throw error(HttpStatus.BAD_REQUEST,"Bài xuất bản cần ảnh bìa, mô tả ngắn và nội dung.");
        if(id!=null && !(body.get("version") instanceof Number)) throw error(HttpStatus.BAD_REQUEST,"Thiếu phiên bản bài viết. Vui lòng tải lại.");
        values.put("version",body.get("version"));
        try {return view(repository.save(id,admin.id(),values,serialized));}
        catch(DataIntegrityViolationException ex) {throw error(HttpStatus.CONFLICT,"Đường dẫn bài viết đã tồn tại. Hãy chọn đường dẫn khác.");}
    }
    private boolean hasContent(Object value) {
        if(value instanceof Map<?,?> map) {if(map.get("text") instanceof String text && !text.isBlank()) return true;return hasContent(map.get("content"));}
        return value instanceof List<?> list && list.stream().anyMatch(this::hasContent);
    }
    private Object timestamp(Object value) {return value instanceof java.sql.Timestamp time?time.toInstant().toString():value==null?null:value.toString();}
    private Instant instant(Object value) {return value instanceof java.time.OffsetDateTime time?time.toInstant():((java.sql.Timestamp)value).toInstant();}
    private Map<String,Object> view(Map<String,Object> row) {
        var data=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","slug","title","excerpt","category","status","version")) data.put(key,row.get(key));
        data.put("coverUrl",row.get("cover_url"));data.put("authorName",row.get("author_name"));data.put("publishedAt",timestamp(row.get("published_at")));data.put("updatedAt",timestamp(row.get("updated_at")));
        if(row.containsKey("document")) data.put("document",json.readValue(row.get("document").toString(),new TypeReference<Map<String,Object>>(){}));
        return data;
    }
    public Map<String,Object> capabilities(String token) {accounts.authenticate(token,true);return Map.of("enabled",cloud.ready(),"maxImageBytes",10485760,"maxVideoBytes",52428800,"maxVideoSeconds",120);}
    public Map<String,Object> sign(String token,Map<String,Object> body) {
        var admin=accounts.authenticate(token,true);cloud.requireReady();String type=text(body,"type",10,true);
        if(!List.of("image","video").contains(type)) throw error(HttpStatus.BAD_REQUEST,"Chỉ hỗ trợ ảnh hoặc video.");
        String publicId="cleanmaster/blog/"+admin.id()+"/"+UUID.randomUUID();long id=repository.reserve(admin.id(),publicId,type);
        return cloud.ticket(id,publicId,type,Instant.now().getEpochSecond());
    }
    public Map<String,Object> complete(String token,long id) {
        var admin=accounts.authenticate(token,true);var row=repository.media(id,admin.id());
        if(row==null) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy tệp của tài khoản này.");
        if(!"READY".equals(row.get("status"))) {
            if(instant(row.get("expires_at")).isBefore(Instant.now())) throw error(HttpStatus.CONFLICT,"Phiên tải tệp đã hết hạn.");
            var asset=cloud.inspect(row.get("public_id").toString(),row.get("resource_type").toString());
            repository.complete(id,admin.id(),asset.get("secure_url").toString());
            row=repository.media(id,admin.id());
        }
        return Map.of("id",id,"url",row.get("secure_url"),"type",row.get("resource_type"));
    }
}
