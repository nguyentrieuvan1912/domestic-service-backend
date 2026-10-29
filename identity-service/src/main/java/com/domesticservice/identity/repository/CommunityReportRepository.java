package com.domesticservice.identity.repository;

import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class CommunityReportRepository {
    private final NativeQueryExecutor db;
    public CommunityReportRepository(NativeQueryExecutor db){this.db=db;}
    public Map<String,Object> report(long comment,long reporter,String reason,String detail){
        var old=db.queryForList("SELECT id,status FROM community_comment_reports WHERE comment_id=? AND reporter_id=?",comment,reporter);
        if(!old.isEmpty())return old.getFirst();
        long id=db.insert("INSERT INTO community_comment_reports(comment_id,reporter_id,reason,detail) VALUES (?,?,?,?)",comment,reporter,reason,detail);
        return Map.of("id",id,"status","OPEN");
    }
    public List<Map<String,Object>> list(String status,Long before){
        return db.queryForList("""
            SELECT r.id,r.comment_id AS "commentId",r.reason,r.detail,r.status,r.resolution,r.moderation_note AS "moderationNote",
             r.created_at AS "createdAt",r.reviewed_at AS "reviewedAt",reviewer.full_name AS "reviewedBy",
             reporter.full_name AS reporter,c.user_id AS "authorId",u.full_name AS author,u.role AS "authorRole",u.status AS "accountStatus",
             c.content,c.media_urls::text AS media,c.created_at AS "commentCreatedAt",(c.deleted_at IS NOT NULL) AS deleted,
             c.post_id AS "postId",p.content AS "postContent",p.status AS "postStatus",owner.full_name AS "postAuthor",
             c.parent_id AS "parentId",parent.content AS "parentContent",parentuser.full_name AS "parentAuthor"
            FROM community_comment_reports r JOIN community_comments c ON c.id=r.comment_id
             JOIN users u ON u.id=c.user_id JOIN users reporter ON reporter.id=r.reporter_id
             JOIN community_posts p ON p.id=c.post_id JOIN users owner ON owner.id=p.user_id
             LEFT JOIN users reviewer ON reviewer.id=r.reviewed_by
             LEFT JOIN community_comments parent ON parent.id=c.parent_id LEFT JOIN users parentuser ON parentuser.id=parent.user_id
            WHERE (?='ALL' OR r.status=?)
            """+(before==null?"":" AND r.id<"+before)+" ORDER BY r.id DESC LIMIT 51",status,status);
    }
    public Map<String,Object> find(long id,boolean lock){
        var rows=db.queryForList("SELECT r.*,c.user_id AS author_id FROM community_comment_reports r JOIN community_comments c ON c.id=r.comment_id WHERE r.id=?"+(lock?" FOR UPDATE OF r":""),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo.");return rows.getFirst();
    }
    public Map<String,Object> lockAuthor(long user){return db.queryForMap("SELECT id,role,status FROM users WHERE id=? FOR UPDATE",user);}
    public void delete(long comment,long admin){
        db.update("UPDATE community_comments SET deleted_at=now(),deleted_by=? WHERE id=? AND deleted_at IS NULL",admin,comment);
    }
    public void resolve(long id,long comment,String action,String note,long admin,boolean all){
        db.update("UPDATE community_comment_reports SET status=?,resolution=?,moderation_note=?,reviewed_by=?,reviewed_at=now() WHERE status='OPEN' AND "+(all?"comment_id=?":"id=?"),
            "DISMISS".equals(action)?"DISMISSED":"RESOLVED",action,note,admin,all?comment:id);
    }
}
