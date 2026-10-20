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
        return db.queryForList("SELECT p.id,p.user_id AS \"userId\",u.full_name AS author,u.avatar_url AS avatar,p.content,p.status,p.created_at AS \"createdAt\",p.moderation_note AS \"moderationNote\",(SELECT count(*) FROM community_likes l WHERE l.post_id=p.id) AS likes,EXISTS(SELECT 1 FROM community_likes l WHERE l.post_id=p.id AND l.user_id=?) AS \"isLiked\" FROM community_posts p JOIN users u ON u.id=p.user_id WHERE p.status='PUBLISHED' OR p.user_id=? OR ? ORDER BY p.created_at DESC,p.id DESC LIMIT 100", args);
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
