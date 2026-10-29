package com.domesticservice.identity.repository;

import com.domesticservice.identity.dto.AdminWorkItem;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminInboxRepository {
    private final JdbcTemplate db;
    public AdminInboxRepository(JdbcTemplate db){this.db=db;}
    public List<Map<String,Object>> localWork(){
        return db.queryForList("""
          SELECT 'candidate:'||id AS key,status AS state,'candidates' AS section,
            '/admin/candidates' AS target,'Ứng viên cần xử lý #'||id AS title,
            CASE status WHEN 'PENDING' THEN 'Hồ sơ mới chờ tiếp nhận' WHEN 'ONBOARDING' THEN 'Đang tiếp nhận hồ sơ' WHEN 'TRAINING' THEN 'Cần theo dõi đào tạo' ELSE 'Chờ đánh giá và cấp tài khoản' END AS summary
          FROM staff_candidates WHERE staff_id IS NULL
          UNION ALL
          SELECT 'support:'||id,status,'reviews','/admin/reviews#support-requests','Yêu cầu hỗ trợ #'||id,subject FROM support_requests WHERE status IN ('RECEIVED','IN_PROGRESS')
          UNION ALL
          SELECT 'post:'||id,status,'reviews','/admin/reviews#community-posts','Bài viết chờ duyệt #'||id,left(content,180) FROM community_posts WHERE status='PENDING'
          UNION ALL
          SELECT 'comment-report:'||id,status,'reviews','/admin/reviews#community-reports','Báo cáo bình luận #'||id,'Bình luận #'||comment_id||' · '||reason FROM community_comment_reports WHERE status='OPEN'
          """);
    }
    @Transactional
    public void apply(long admin,String source,List<AdminWorkItem> items){
        // Serialize queue materialization across tabs and backend instances for this admin.
        db.queryForList("SELECT pg_advisory_xact_lock(?)",admin);
        var keys=items.stream().map(AdminWorkItem::key).toList();
        if(keys.isEmpty()) db.update("UPDATE admin_inbox_entries SET active=false WHERE user_id=? AND source=? AND active",admin,source);
        else {
            var args=new ArrayList<Object>();args.add(admin);args.add(source);args.addAll(keys);
            db.update("UPDATE admin_inbox_entries SET active=false WHERE user_id=? AND source=? AND active AND source_key NOT IN ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",args.toArray());
        }
        for(var item:items) {
            db.update("UPDATE admin_inbox_entries SET active=false WHERE user_id=? AND source=? AND source_key=? AND state<>? AND active",admin,source,item.key(),item.state());
            db.update("""
              INSERT INTO admin_inbox_entries(user_id,source,source_key,event_key,state,section,target,title,summary)
              VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,source,source_key) WHERE active DO UPDATE SET title=EXCLUDED.title,summary=EXCLUDED.summary,target=EXCLUDED.target
              WHERE (admin_inbox_entries.title,admin_inbox_entries.summary,admin_inbox_entries.target) IS DISTINCT FROM (EXCLUDED.title,EXCLUDED.summary,EXCLUDED.target)
              """,admin,source,item.key(),UUID.randomUUID().toString(),item.state(),item.section(),item.target(),item.title(),item.summary());
        }
    }

    public Map<String,Object> snapshot(long admin,Long before){return snapshot(admin,before,"all");}
    public Map<String,Object> snapshot(long admin,Long before,String filter){
        String condition=switch(filter){case "unread"->" AND read_at IS NULL";case "pending"->" AND active";default->"";};
        var rows=db.queryForList("""
          SELECT id,source_key AS "sourceKey",section,target,title,summary,active,read_at AS "readAt",created_at AS "createdAt"
          FROM admin_inbox_entries WHERE user_id=? AND id<?
          """+condition+" ORDER BY id DESC LIMIT 51",admin,before==null?Long.MAX_VALUE:before);
        boolean more=rows.size()>50;
        var items=rows.stream().limit(50).toList();
        var counts=new LinkedHashMap<String,Long>();
        db.queryForList("SELECT section,count(*) AS count FROM admin_inbox_entries WHERE user_id=? AND active GROUP BY section",admin)
          .forEach(row->counts.put(row.get("section").toString(),((Number)row.get("count")).longValue()));
        var targets=new LinkedHashMap<String,Long>();
        db.queryForList("SELECT target,count(*) AS count FROM admin_inbox_entries WHERE user_id=? AND active GROUP BY target",admin)
          .forEach(row->targets.put(row.get("target").toString(),((Number)row.get("count")).longValue()));
        return Map.of("pendingTargets",targets,"items",items,"hasMore",more,"nextBefore",items.isEmpty()?0:items.getLast().get("id"),
          "unread",db.queryForObject("SELECT count(*) FROM admin_inbox_entries WHERE user_id=? AND read_at IS NULL",Long.class,admin),
          "pending",counts,"totalPending",counts.values().stream().mapToLong(Long::longValue).sum());
    }
    public boolean markRead(long admin,long id){return db.update("UPDATE admin_inbox_entries SET read_at=coalesce(read_at,now()) WHERE user_id=? AND id=?",admin,id)>0;}
    public void readAll(long admin,long through){db.update("UPDATE admin_inbox_entries SET read_at=now() WHERE user_id=? AND id<=? AND read_at IS NULL",admin,through);}
}
