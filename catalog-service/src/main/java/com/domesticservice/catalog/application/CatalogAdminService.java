package com.domesticservice.catalog.application;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CatalogAdminService {
    private final JdbcTemplate db; private final ObjectMapper json; private final ServiceHttpClient identity;
    public CatalogAdminService(JdbcTemplate db,ObjectMapper json,@Value("${clients.identity.base-url}") String url) { this.db=db;this.json=json;identity=new ServiceHttpClient(url); }
    public void admin(String token) { identity.authenticate(token,"ADMIN"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private String text(Map<String,Object> body,String key,int max,boolean required) {
        String value=Objects.toString(body.get(key),"").strip(); if(value.length()>max || required && value.isBlank()) throw bad("Vui lòng kiểm tra "+key+"."); return value;
    }
    private long number(Map<String,Object> body,String key,long fallback,long min,long max) {
        try { long value=body.get(key)==null?fallback:Long.parseLong(body.get(key).toString()); if(value<min || value>max) throw bad("Giá trị "+key+" không hợp lệ."); return value; }
        catch(NumberFormatException ex) { throw bad("Giá trị "+key+" không hợp lệ."); }
    }
    private long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder(); db.update(c->{var s=c.prepareStatement(sql,new String[]{"id"}); for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]); return s;},key);return Objects.requireNonNull(key.getKey()).longValue();
    }
    private void exists(String table,long id) { if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=?)",Boolean.class,id))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bản ghi."); }
    public Map<String,Object> snapshot(String token) {
        admin(token);
        var categories=db.queryForList("SELECT id,category_name AS name,description,status FROM service_categories ORDER BY id");
        var services=db.queryForList("SELECT s.id,s.service_name AS name,s.description,s.category_id AS \"categoryId\",c.category_name AS \"categoryName\",s.status,s.service_code AS code,s.service_type AS \"serviceType\",s.price_unit AS \"priceUnit\",s.base_price AS \"basePrice\",s.estimated_duration_minutes AS \"durationMinutes\",s.requires_qualification AS \"requiresQualification\" FROM services s JOIN service_categories c ON c.id=s.category_id ORDER BY s.id");
        for(var service:services) {
            service.put("isActive","ACTIVE".equals(service.get("status")));service.put("icon","DV");
            var fields=db.queryForList("SELECT id,field_key AS \"fieldKey\",label AS \"fieldName\",lower(field_type) AS \"fieldType\",required AS \"isRequired\",options::text AS options FROM service_requirements WHERE service_id=? AND status='ACTIVE' ORDER BY display_order,id",service.get("id"));
            fields.forEach(f->f.put("options",json.readValue((String)f.get("options"),new TypeReference<List<String>>(){}))); service.put("dynamicFields",fields);
        }
        var packages=db.queryForList("SELECT id,service_id AS \"serviceId\",package_name AS name,description,base_price AS price,duration_minutes AS \"durationMinutes\",status,default_staff_count AS \"defaultStaffCount\",max_area AS \"maxArea\" FROM service_packages ORDER BY id");
        var addons=db.queryForList("SELECT id,service_id AS \"serviceId\",name,description,price,extra_duration_minutes AS \"durationMinutes\",status FROM add_ons ORDER BY id");
        for(var list:List.of(packages,addons)) list.forEach(p->{p.put("duration",p.get("durationMinutes")+" phút");p.put("isActive","ACTIVE".equals(p.get("status")));});
        return Map.of("categories",categories,"services",services,"packages",packages,"addOns",addons);
    }
    @Transactional public Map<String,Object> save(String token,String kind,Long id,Map<String,Object> body) {
        admin(token); String name=text(body,"name",160,true), description=text(body,"description",10000,false);
        String status=Boolean.FALSE.equals(body.get("isActive"))?"INACTIVE":"ACTIVE";
        switch(kind) {
            case "categories" -> {
                if(id==null) id=insert("INSERT INTO service_categories(category_code,category_name,description,status) VALUES (?,?,?,?)","CUSTOM-"+UUID.randomUUID(),name,description,status);
                else {exists("service_categories",id); db.update("UPDATE service_categories SET category_name=?,description=?,status=?,updated_at=now() WHERE id=?",name,description,status,id);}
            }
            case "services" -> {
                long category=number(body,"categoryId",0,1,Long.MAX_VALUE);exists("service_categories",category);
                if(id==null) id=insert("INSERT INTO services(category_id,service_code,service_name,description,service_type,price_unit,base_price,estimated_duration_minutes,status) VALUES (?,?,?,?,?,?,?,?,?)",category,"CUSTOM-"+UUID.randomUUID(),name,description,text(body,"serviceType",60,false).isBlank()?"GENERAL":body.get("serviceType"),text(body,"priceUnit",30,false).isBlank()?"PACKAGE":body.get("priceUnit"),number(body,"basePrice",0,0,1000000000),number(body,"durationMinutes",60,30,720),status);
                else {exists("services",id);db.update("UPDATE services SET category_id=?,service_name=?,description=?,status=?,updated_at=now(),version=version+1 WHERE id=?",category,name,description,status,id);}
                if(body.containsKey("dynamicFields")) {
                    if(!(body.get("dynamicFields") instanceof List<?> fields) || fields.size()>50)throw bad("Yêu cầu dịch vụ không hợp lệ.");
                    db.update("UPDATE service_requirements SET status='INACTIVE' WHERE service_id=?",id);
                    int order=0;
                    for(Object entry:fields) {
                        if(!(entry instanceof Map<?,?> raw))throw bad("Yêu cầu dịch vụ không hợp lệ.");
                        @SuppressWarnings("unchecked") var field=(Map<String,Object>)raw;
                        String label=text(field,"fieldName",160,true),type=text(field,"fieldType",30,true).toUpperCase(Locale.ROOT);
                        if(!List.of("TEXT","NUMBER","SELECT","BOOLEAN","MULTI_SELECT","DATE").contains(type))throw bad("Kiểu câu hỏi không hợp lệ.");
                        String key=Objects.toString(field.get("fieldKey"),"field_"+order);
                        long fieldId=number(field,"id",0,0,Long.MAX_VALUE);
                        if(fieldId>0) {
                            int changed=db.update("UPDATE service_requirements SET label=?,field_type=?,required=?,options=CAST(? AS jsonb),display_order=?,status='ACTIVE' WHERE id=? AND service_id=?",label,type,Boolean.TRUE.equals(field.get("isRequired")),json.writeValueAsString(field.getOrDefault("options",List.of())),order++,fieldId,id);
                            if(changed==0)throw bad("Câu hỏi không thuộc dịch vụ.");
                        } else insert("INSERT INTO service_requirements(service_id,field_key,label,field_type,required,options,display_order) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",id,key+"_"+UUID.randomUUID().toString().substring(0,8),label,type,Boolean.TRUE.equals(field.get("isRequired")),json.writeValueAsString(field.getOrDefault("options",List.of())),order++);
                    }
                }
            }
            case "packages","addons" -> {
                long service=number(body,"serviceId",0,1,Long.MAX_VALUE);exists("services",service);
                long price=number(body,"price",0,0,1000000000),minutes=duration(body,kind.equals("packages")?30:0);
                if(kind.equals("packages")) {
                    if(id==null) id=insert("INSERT INTO service_packages(service_id,package_name,description,base_price,duration_minutes,default_staff_count,status) VALUES (?,?,?,?,?,?,?)",service,name,description,price,minutes,number(body,"defaultStaffCount",1,1,20),status);
                    else {exists("service_packages",id);db.update("UPDATE service_packages SET service_id=?,package_name=?,description=?,base_price=?,duration_minutes=?,status=?,updated_at=now() WHERE id=?",service,name,description,price,minutes,status,id);}
                } else {
                    if(id==null) id=insert("INSERT INTO add_ons(service_id,name,description,price,extra_duration_minutes,status) VALUES (?,?,?,?,?,?)",service,name,description,price,minutes,status);
                    else {exists("add_ons",id);db.update("UPDATE add_ons SET service_id=?,name=?,description=?,price=?,extra_duration_minutes=?,status=?,updated_at=now() WHERE id=?",service,name,description,price,minutes,status,id);}
                }
            }
            default -> throw bad("Loại danh mục không hợp lệ.");
        }
        return Map.of("id",id);
    }
    private long duration(Map<String,Object> body,long min) {
        if(body.containsKey("durationMinutes"))return number(body,"durationMinutes",60,min,720);
        String duration=text(body,"duration",100,true).toLowerCase(Locale.ROOT).replace(',','.');
        var match=java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(giờ|tiếng|phút|h|min)").matcher(duration);
        if(!match.find())throw bad("Thời lượng cần ghi rõ số giờ hoặc phút.");
        long value=Math.round(Double.parseDouble(match.group(1))*(List.of("giờ","tiếng","h").contains(match.group(2))?60:1));
        if(value<min || value>720)throw bad("Thời lượng không hợp lệ.");return value;
    }
    public List<Map<String,Object>> promotions(String token) {
        admin(token); return db.queryForList("SELECT id,promotion_code AS code,promotion_name AS name,description,CASE discount_type WHEN 'PERCENTAGE' THEN 'PERCENT' ELSE 'FIXED' END AS \"discountType\",discount_value AS \"discountValue\",max_discount_amount AS \"maxDiscount\",minimum_booking_amount AS \"minimumBookingAmount\",start_at AS \"startsAt\",end_at AS \"endsAt\",usage_limit AS \"usageLimit\",used_count AS \"usedCount\",CASE WHEN status='INACTIVE' THEN 'DISABLED' WHEN end_at<=now() THEN 'EXPIRED' WHEN start_at>now() THEN 'SCHEDULED' ELSE 'ACTIVE' END AS status FROM promotions ORDER BY id DESC");
    }
    @Transactional public Map<String,Object> savePromotion(String token,Long id,Map<String,Object> body) {
        admin(token);String code=text(body,"code",60,true).toUpperCase(Locale.ROOT),description=text(body,"description",10000,true);
        if(!code.matches("[A-Z0-9_-]{3,60}"))throw bad("Mã ưu đãi không hợp lệ.");
        java.time.OffsetDateTime start,end;
        try {start=java.time.LocalDate.parse(text(body,"validFrom",10,true)).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(7));end=java.time.LocalDate.parse(text(body,"validTo",10,true)).plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(7));}catch(java.time.DateTimeException ex){throw bad("Ngày ưu đãi không hợp lệ.");}
        if(!end.isAfter(start))throw bad("Ngày kết thúc phải sau ngày bắt đầu.");
        String type=text(body,"discountType",20,true);if(!List.of("PERCENT","FIXED").contains(type))throw bad("Loại giảm giá không hợp lệ.");
        long value=number(body,"discountValue",0,0,type.equals("PERCENT")?100:1000000000),limit=number(body,"usageLimit",0,1,Integer.MAX_VALUE);
        Object max=body.get("maxDiscount")==null?null:number(body,"maxDiscount",0,0,1000000000);
        String state="DISABLED".equals(body.get("status"))?"INACTIVE":"ACTIVE";
        if(id==null)id=insert("INSERT INTO promotions(promotion_code,promotion_name,description,start_at,end_at,discount_type,discount_value,max_discount_amount,minimum_booking_amount,usage_limit,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",code,Objects.toString(body.get("name"),code),description,start,end,type.equals("PERCENT")?"PERCENTAGE":"FIXED_AMOUNT",value,max,number(body,"minimumBookingAmount",0,0,1000000000),limit,state);
        else {exists("promotions",id);int used=db.queryForObject("SELECT used_count FROM promotions WHERE id=? FOR UPDATE",Integer.class,id);if(limit<used)throw bad("Giới hạn không được nhỏ hơn số lượt đã dùng.");db.update("UPDATE promotions SET promotion_code=?,description=?,start_at=?,end_at=?,discount_type=?,discount_value=?,max_discount_amount=?,usage_limit=?,status=? WHERE id=?",code,description,start,end,type.equals("PERCENT")?"PERCENTAGE":"FIXED_AMOUNT",value,max,limit,state,id);}
        return Map.of("id",id);
    }
}
