package com.domesticservice.identity.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import static com.domesticservice.identity.service.AccountService.error;

@Repository
public class BlogRepository {
    private final JdbcTemplate db;
    public BlogRepository(JdbcTemplate db) { this.db=db; }
    public List<Map<String,Object>> list(boolean admin) {
        return db.queryForList("SELECT id,slug,title,excerpt,category,cover_url,author_name,status,version,published_at,updated_at FROM blog_articles"
                +(admin ? "" : " WHERE status='PUBLISHED'")+" ORDER BY COALESCE(published_at,created_at) DESC,id DESC LIMIT 200");
    }
    public Map<String,Object> find(long id) { return first(db.queryForList("SELECT * FROM blog_articles WHERE id=?",id)); }
    public Map<String,Object> published(String slug) { return first(db.queryForList("SELECT * FROM blog_articles WHERE slug=? AND status='PUBLISHED'",slug)); }
    private Map<String,Object> first(List<Map<String,Object>> rows) { return rows.isEmpty()?null:rows.getFirst(); }
    @Transactional
    public Map<String,Object> save(Long id, long user, Map<String,Object> data, String document) {
        if(id==null) {
            var keys=new GeneratedKeyHolder();
            db.update(connection -> {
                var statement=connection.prepareStatement("INSERT INTO blog_articles(slug,title,excerpt,category,cover_url,author_name,document,status,created_by,updated_by,published_at) VALUES (?,?,?,?,?,?,?,?,?,?,CASE WHEN ?='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END)",new String[]{"id"});
                Object[] args={data.get("slug"),data.get("title"),data.get("excerpt"),data.get("category"),data.get("coverUrl"),data.get("authorName"),document,data.get("status"),user,user,data.get("status")};
                for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);return statement;
            },keys);
            id=Objects.requireNonNull(keys.getKey()).longValue();
        } else {
            int changed=db.update("UPDATE blog_articles SET slug=?,title=?,excerpt=?,category=?,cover_url=?,author_name=?,document=?,status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1,published_at=CASE WHEN ?='PUBLISHED' THEN COALESCE(published_at,CURRENT_TIMESTAMP) ELSE published_at END WHERE id=? AND version=?",
                    data.get("slug"),data.get("title"),data.get("excerpt"),data.get("category"),data.get("coverUrl"),data.get("authorName"),document,data.get("status"),user,data.get("status"),id,data.get("version"));
            if(changed!=1) throw error(HttpStatus.CONFLICT,"Bài viết đã thay đổi hoặc không còn tồn tại. Hãy tải lại trước khi lưu.");
        }
        return find(id);
    }
    @Transactional
    public long reserve(long owner,String publicId,String type) {
        var keys=new GeneratedKeyHolder();
        db.update(connection -> {
            var s=connection.prepareStatement("INSERT INTO blog_media(owner_id,public_id,resource_type,expires_at) VALUES (?,?,?,?)",new String[]{"id"});
            s.setLong(1,owner);s.setString(2,publicId);s.setString(3,type);s.setTimestamp(4,java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(600)));return s;
        },keys);return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public Map<String,Object> media(long id,long owner) { return first(db.queryForList("SELECT * FROM blog_media WHERE id=? AND owner_id=?",id,owner)); }
    public void complete(long id,long owner,String url) { db.update("UPDATE blog_media SET secure_url=?,status='READY' WHERE id=? AND owner_id=?",url,id,owner); }
}
