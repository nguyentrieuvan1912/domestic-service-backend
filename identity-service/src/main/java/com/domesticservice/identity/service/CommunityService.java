package com.domesticservice.identity.service;

import com.domesticservice.identity.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommunityService {
    private final AccountService accounts;
    private final CommunitySqlRepository repository;
    private final CommunityMediaRepository media;
    private final ObjectMapper json;
    public CommunityService(AccountService accounts,CommunitySqlRepository repository,CommunityMediaRepository media,ObjectMapper json){this.accounts=accounts;this.repository=repository;this.media=media;this.json=json;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private String content(Map<String,Object> b,int max,boolean optional){
        if(b.get("content")!=null && !(b.get("content") instanceof String))throw bad("Nội dung không hợp lệ.");
        String value=Objects.toString(b.get("content"),"").strip();
        if((!optional && value.isEmpty()) || value.length()>max)throw bad("Nội dung không hợp lệ.");return value;
    }
    private String key(Map<String,Object> body){
        String value=Objects.toString(body.get("requestKey"),UUID.randomUUID().toString());
        if(!value.matches("[A-Za-z0-9_-]{1,160}"))throw bad("Mã yêu cầu không hợp lệ.");return value;
    }
    private long user(String token){return token==null?0:accounts.authenticate(token,false).id();}
    private Map<String,Object> decorate(Map<String,Object> row,boolean admin){
        row.put("media",json.readValue(row.get("media").toString(),new TypeReference<List<Map<String,Object>>>(){}));
        row.put("reactions",json.readValue(row.get("reactions").toString(),new TypeReference<Map<String,Object>>(){}));
        row.put("isLiked",row.get("myReaction")!=null);row.put("comments",List.of());
        if(!admin)row.remove("moderationNote");return row;
    }
    public List<Map<String,Object>> list(String token){long user=user(token);return repository.postsExistsCommunityLikes(user,user,false).stream().map(p->decorate(p,false)).toList();}
    public List<Map<String,Object>> admin(String token){long user=accounts.authenticate(token,true).id();return repository.postsExistsCommunityLikes(user,user,true).stream().map(p->decorate(p,true)).toList();}
    public Map<String,Object> post(String token,long id){
        var user=token==null?null:accounts.authenticate(token,false);long uid=user==null?0:user.id();
        if(!Boolean.TRUE.equals(repository.visibleExistsCommunityPosts(Boolean.class,id,uid,user!=null && "ADMIN".equals(user.role()))))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài viết.");
        return decorate(repository.post(id,uid),user!=null && "ADMIN".equals(user.role()));
    }
    @Transactional
    public Map<String,Object> create(String token,Map<String,Object> b){
        var user=accounts.authenticate(token,false);String content=content(b,5000,true),key=key(b);
        if(b.get("media") instanceof List<?> raw && !raw.isEmpty())throw bad("Hãy tải tệp lên Cloudinary và gửi mã tệp đã xác minh.");
        var ids=mediaIds(b);
        if(content.isBlank() && ids.isEmpty())throw bad("Nhập nội dung hoặc chọn ảnh/video.");
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((content+"\n"+ids).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        repository.lockUser(user.id());var old=repository.existingPost(user.id(),key);
        if(old!=null){if(!hash.equals(old.get("request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã yêu cầu đã dùng cho bản nháp khác.");return Map.of("id",old.get("id"),"status",old.get("status"));}
        var selected=verifiedMedia(ids,user.id());String status="ADMIN".equals(user.role())?"PUBLISHED":"PENDING";
        long id=repository.createPost(user.id(),content,status,json.writeValueAsString(selected),key,hash);
        ids.forEach(asset->media.attach(asset,id));return Map.of("id",id,"status",status);
    }
    public Map<String,Object> comments(String token,long id,Long before,Long after){
        post(token,id);long uid=user(token);
        if(before!=null && after!=null || before!=null && before<=0 || after!=null && after<0)throw bad("Mốc bình luận không hợp lệ.");
        var rows=new ArrayList<>(repository.comments(id,uid,before,after));boolean more=rows.size()>50;if(more)rows.removeLast();
        if(after==null)Collections.reverse(rows);
        rows.forEach(this::decorateComment);
        return Map.of("items",rows,"hasMore",more,"nextBefore",rows.isEmpty()?0:rows.getFirst().get("id"),"deletedIds",repository.deletedComments(id));
    }
    @Transactional
    public Map<String,Object> comment(String token,long id,Map<String,Object> b){
        var user=accounts.authenticate(token,false);String content=content(b,2000,true),key=key(b);var ids=mediaIds(b);Long parent=null;
        if(content.isEmpty() && ids.isEmpty())throw bad("Nhập bình luận hoặc chọn ảnh/video.");
        if(b.get("parentId")!=null){if(!(b.get("parentId") instanceof Number n) || n.longValue()<=0 || n.doubleValue()!=n.longValue())throw bad("Bình luận trả lời không hợp lệ.");parent=n.longValue();}
        repository.lockUser(user.id());repository.lockVisible(id,user.id(),"ADMIN".equals(user.role()));
        var old=repository.existingComment(user.id(),key);
        if(old!=null){if(((Number)old.get("post_id")).longValue()!=id || !Objects.equals(old.get("parent_id"),parent) || !content.equals(old.get("content")) || !ids.equals(json.readValue(old.get("media").toString(),new TypeReference<List<Map<String,Object>>>(){}).stream().map(m->((Number)m.get("id")).longValue()).toList()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã gửi đã dùng cho bình luận khác.");return commentView(id,user.id(),((Number)old.get("id")).longValue());}
        if(parent!=null)repository.lockComment(id,parent);
        var selected=verifiedMedia(ids,user.id());
        long comment=repository.comment(id,user.id(),content,parent,key,json.writeValueAsString(selected));
        ids.forEach(asset->media.attachComment(asset,comment));return commentView(id,user.id(),comment);
    }
    private Map<String,Object> commentView(long post,long user,long comment){
        var row=repository.comments(post,user,comment+1,null).getFirst();
        decorateComment(row);return row;
    }
    private void decorateComment(Map<String,Object> row){
        row.put("media",json.readValue(row.get("media").toString(),new TypeReference<List<Map<String,Object>>>(){}));
        row.put("reactions",Boolean.TRUE.equals(row.get("deleted"))?Map.of():json.readValue(row.get("reactions").toString(),new TypeReference<Map<String,Object>>(){}));
        if(Boolean.TRUE.equals(row.get("deleted")))row.put("myReaction",null);
    }
    private List<Long> mediaIds(Map<String,Object> b){
        if(b.get("media") instanceof List<?> raw && !raw.isEmpty())throw bad("Chỉ nhận mã tệp Cloudinary đã xác minh.");
        var ids=new ArrayList<Long>();
        if(b.get("mediaIds")!=null){
            if(!(b.get("mediaIds") instanceof List<?> raw) || raw.size()>4)throw bad("Tối đa 4 tệp cho mỗi bài viết hoặc bình luận.");
            for(Object value:raw){if(!(value instanceof Number n) || n.longValue()<=0 || n.doubleValue()!=n.longValue() || ids.contains(n.longValue()))throw bad("Mã tệp không hợp lệ.");ids.add(n.longValue());}
        }return ids;
    }
    private List<Map<String,Object>> verifiedMedia(List<Long> ids,long user){
        var assets=new HashMap<Long,Map<String,Object>>();
        for(long id:ids.stream().sorted().toList()){
            var asset=media.find(id,user,true);
            if(asset==null || !"READY".equals(asset.get("status")))throw bad("Tệp chưa được xác minh, đã dùng hoặc không thuộc tài khoản của bạn.");
            assets.put(id,CommunityMediaService.view(asset));
        }return ids.stream().map(assets::get).toList();
    }
    private String reaction(Map<String,Object> b){
        if(!b.containsKey("reaction"))throw bad("Thiếu cảm xúc.");
        if(b.get("reaction")==null)return null;String value=Objects.toString(b.get("reaction"),"");
        if(!List.of("LIKE","LOVE","HAHA","WOW","SAD","SUPPORT").contains(value))throw bad("Cảm xúc không hợp lệ.");return value;
    }
    @Transactional public Map<String,Object> react(String token,long id,Map<String,Object> b){
        var user=accounts.authenticate(token,false);String reaction=reaction(b);repository.lockUser(user.id());repository.lockVisible(id,user.id(),"ADMIN".equals(user.role()));repository.reaction(id,user.id(),reaction);return decorate(repository.post(id,user.id()),"ADMIN".equals(user.role()));
    }
    @Transactional public Map<String,Object> commentReaction(String token,long post,long comment,Map<String,Object> b){
        var user=accounts.authenticate(token,false);String reaction=reaction(b);repository.lockUser(user.id());repository.lockVisible(post,user.id(),"ADMIN".equals(user.role()));
        repository.lockComment(post,comment);repository.commentReaction(comment,user.id(),reaction);
        return commentView(post,user.id(),comment);
    }
    @Transactional public Map<String,Object> like(String token,long id,Map<String,Object> b){
        if(!(b.get("liked") instanceof Boolean liked))throw bad("Trạng thái thích không hợp lệ.");
        var body=new HashMap<String,Object>();body.put("reaction",liked?"LIKE":null);return react(token,id,body);
    }
    @Transactional public Map<String,Object> moderate(String token,long id,Map<String,Object> b){
        var user=accounts.authenticate(token,true);String status=Objects.toString(b.get("status"),""),note=Objects.toString(b.get("note"),"");
        if(!List.of("PENDING","PUBLISHED","HIDDEN").contains(status) || note.length()>2000)throw bad("Trạng thái kiểm duyệt không hợp lệ.");
        if(repository.moderateUpdateCommunityPosts(status,note,user.id(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài viết.");return Map.of("id",id,"status",status);
    }
}
