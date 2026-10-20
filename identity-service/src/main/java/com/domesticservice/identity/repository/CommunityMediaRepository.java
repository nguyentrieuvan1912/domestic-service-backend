package com.domesticservice.identity.repository;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class CommunityMediaRepository {
    private final NativeQueryExecutor db;
    public CommunityMediaRepository(NativeQueryExecutor db){this.db=db;}
    public long reserve(long user,String publicId,String type){
        db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",user);
        if(db.queryForObject("SELECT count(*) FROM community_media WHERE user_id=? AND created_at>now()-interval '1 hour'",Long.class,user)>=20)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Bạn đã chọn nhiều tệp trong giờ này. Vui lòng thử lại sau.");
        return db.insert("INSERT INTO community_media(user_id,public_id,resource_type) VALUES (?,?,?)",user,publicId,type);
    }
    public Map<String,Object> find(long id,long user,boolean lock){
        var rows=db.queryForList("SELECT * FROM community_media WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,user);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void verified(long id,Map<String,Object> asset){
        db.update("UPDATE community_media SET status='READY',secure_url=?,bytes=?,width=?,height=?,duration=? WHERE id=? AND status='RESERVED'",
            asset.get("secure_url"),asset.get("bytes"),asset.get("width"),asset.get("height"),asset.getOrDefault("duration",0),id);
    }
    public void attach(long id,long post){db.update("UPDATE community_media SET status='ATTACHED',post_id=? WHERE id=?",post,id);}
    public void attachComment(long id,long comment){db.update("UPDATE community_media SET status='ATTACHED',comment_id=? WHERE id=?",comment,id);}
}
