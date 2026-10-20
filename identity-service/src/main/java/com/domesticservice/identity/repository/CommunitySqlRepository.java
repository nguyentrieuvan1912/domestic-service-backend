package com.domesticservice.identity.repository;

import java.util.*;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class CommunitySqlRepository {

    private final NativeQueryExecutor db;

    public CommunitySqlRepository(NativeQueryExecutor db) {
        this.db = db;
    }

    private long insert(String sql, Object... values) {
        return db.insert(sql, values);
    }

    public <T> T visibleExistsCommunityPosts(Class<T> type, Object... args) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM community_posts WHERE id=? AND (status='PUBLISHED' OR user_id=? OR ?))", type, args);
    }

    public List<Map<String, Object>> postsExistsCommunityLikes(Object... args) {
        return db.queryForList(postSelect()+" WHERE p.status='PUBLISHED' OR p.user_id=? OR ? ORDER BY p.created_at DESC,p.id DESC LIMIT 100", args);
    }

    private String postSelect(){return "SELECT p.id,p.user_id AS \"userId\",u.full_name AS author,u.avatar_url AS avatar,p.content,p.status,p.media_urls::text AS media,p.created_at AS \"createdAt\",p.moderation_note AS \"moderationNote\",(SELECT count(*) FROM community_likes l WHERE l.post_id=p.id) AS likes,(SELECT reaction FROM community_likes l WHERE l.post_id=p.id AND l.user_id=?) AS \"myReaction\",(SELECT count(*) FROM community_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) AS \"commentCount\",COALESCE((SELECT jsonb_object_agg(reaction,n) FROM (SELECT reaction,count(*) n FROM community_likes l WHERE l.post_id=p.id GROUP BY reaction) r),'{}'::jsonb)::text AS reactions FROM community_posts p JOIN users u ON u.id=p.user_id";}
    public Map<String,Object> post(long id,long user){return db.queryForMap(postSelect()+" WHERE p.id=?",user,id);}
    public void lockVisible(long id,long user,boolean admin){
        if(db.queryForList("SELECT id FROM community_posts WHERE id=? AND (status='PUBLISHED' OR user_id=? OR ?) FOR SHARE",id,user,admin).isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy bài viết.");
    }
    public List<Map<String,Object>> comments(long post,long user,Long before,Long after){
        return db.queryForList("SELECT c.id,c.user_id AS \"userId\",CASE WHEN c.deleted_at IS NULL THEN c.content ELSE 'Bình luận đã được quản trị viên xóa.' END AS content,(c.deleted_at IS NOT NULL) AS deleted,CASE WHEN c.deleted_at IS NULL THEN c.media_urls ELSE '[]'::jsonb END::text AS media,u.full_name AS author,u.avatar_url AS avatar,c.created_at AS \"createdAt\",c.parent_id AS \"parentId\",CASE WHEN parent.deleted_at IS NULL THEN parent.content ELSE 'Bình luận đã được quản trị viên xóa.' END AS \"parentContent\",pu.full_name AS \"parentAuthor\",(SELECT reaction FROM community_comment_reactions r WHERE r.comment_id=c.id AND r.user_id=?) AS \"myReaction\",COALESCE((SELECT jsonb_object_agg(reaction,n) FROM (SELECT reaction,count(*) n FROM community_comment_reactions r WHERE r.comment_id=c.id GROUP BY reaction) counts),'{}'::jsonb)::text AS reactions FROM community_comments c JOIN users u ON u.id=c.user_id LEFT JOIN community_comments parent ON parent.id=c.parent_id LEFT JOIN users pu ON pu.id=parent.user_id WHERE c.post_id=?"+(before!=null?" AND c.id<"+before:after!=null?" AND c.id>"+after:"")+" ORDER BY c.id "+(after!=null?"ASC":"DESC")+" LIMIT 51",user,post);
    }
    public Map<String,Object> existingPost(long user,String key){var rows=db.queryForList("SELECT id,status,request_hash FROM community_posts WHERE user_id=? AND request_key=?",user,key);return rows.isEmpty()?null:rows.getFirst();}
    public void lockUser(long user){
        var row=db.queryForMap("SELECT id,status FROM users WHERE id=? FOR UPDATE",user);
        if(!"ACTIVE".equals(row.get("status")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Tài khoản đã bị chặn.");
    }
    public List<Long> deletedComments(long post){return db.queryForList("SELECT id FROM community_comments WHERE post_id=? AND deleted_at IS NOT NULL",Long.class,post);}
    public void lockComment(long post,long comment){
        if(db.queryForList("SELECT id FROM community_comments WHERE post_id=? AND id=? AND deleted_at IS NULL FOR SHARE",post,comment).isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Bình luận đã bị xóa hoặc không tồn tại.");
    }
    public long createPost(long user,String content,String status,String media,String key,String hash){return db.insert("INSERT INTO community_posts(user_id,content,status,media_urls,request_key,request_hash) VALUES (?,?,?,CAST(? AS jsonb),?,?)",user,content,status,media,key,hash);}
    public Map<String,Object> existingComment(long user,String key){var rows=db.queryForList("SELECT id,post_id,parent_id,content,media_urls::text AS media FROM community_comments WHERE user_id=? AND request_key=?",user,key);return rows.isEmpty()?null:rows.getFirst();}
    public long comment(long post,long user,String content,Long parent,String key,String media){return db.insert("INSERT INTO community_comments(post_id,user_id,content,parent_id,request_key,media_urls) VALUES (?,?,?,?,?,CAST(? AS jsonb))",post,user,content,parent,key,media);}
    public boolean commentExists(long post,long comment){return db.queryForObject("SELECT EXISTS(SELECT 1 FROM community_comments WHERE post_id=? AND id=? AND deleted_at IS NULL)",Boolean.class,post,comment);}
    public void reaction(long post,long user,String reaction){
        if(reaction==null)db.update("DELETE FROM community_likes WHERE post_id=? AND user_id=?",post,user);
        else db.update("INSERT INTO community_likes(post_id,user_id,reaction) VALUES (?,?,?) ON CONFLICT(post_id,user_id) DO UPDATE SET reaction=excluded.reaction",post,user,reaction);
    }
    public void commentReaction(long comment,long user,String reaction){
        if(reaction==null)db.update("DELETE FROM community_comment_reactions WHERE comment_id=? AND user_id=?",comment,user);
        else db.update("INSERT INTO community_comment_reactions(comment_id,user_id,reaction) VALUES (?,?,?) ON CONFLICT(comment_id,user_id) DO UPDATE SET reaction=excluded.reaction",comment,user,reaction);
    }

    public List<Map<String, Object>> postsFindCommunityComments(Object... args) {
        return db.queryForList("SELECT c.id,c.content,u.full_name AS author,c.created_at AS \"createdAt\" FROM community_comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? ORDER BY c.id LIMIT 200", args);
    }

    public <T> T createFindCommunityPosts(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO community_posts(user_id,content,status) VALUES (?,?,?) RETURNING id", type, args);
    }

    public <T> T commentFindCommunityComments(Class<T> type, Object... args) {
        return db.queryForObject("INSERT INTO community_comments(post_id,user_id,content) VALUES (?,?,?) RETURNING id", type, args);
    }

    public int likeInsertCommunityLikes(Object... args) {
        return db.update("INSERT INTO community_likes(post_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", args);
    }

    public int likeDeleteCommunityLikes(Object... args) {
        return db.update("DELETE FROM community_likes WHERE post_id=? AND user_id=?", args);
    }

    public <T> T likeCountCommunityLikes(Class<T> type, Object... args) {
        return db.queryForObject("SELECT count(*) FROM community_likes WHERE post_id=?", type, args);
    }

    public int moderateUpdateCommunityPosts(Object... args) {
        return db.update("UPDATE community_posts SET status=?,moderation_note=?,reviewed_by=?,updated_at=now() WHERE id=?", args);
    }
}
