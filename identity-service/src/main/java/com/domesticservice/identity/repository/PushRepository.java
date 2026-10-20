package com.domesticservice.identity.repository;
import com.domesticservice.identity.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class PushRepository {
    private final NativeQueryExecutor db;
    public PushRepository(NativeQueryExecutor db){this.db=db;}
    public void register(long user,String token,String platform,String hash){db.update("INSERT INTO identity_push_devices(user_id,token,platform,session_hash) VALUES (?,?,?,?) ON CONFLICT(token) DO UPDATE SET user_id=excluded.user_id,platform=excluded.platform,session_hash=excluded.session_hash,enabled=true,created_at=CASE WHEN identity_push_devices.user_id<>excluded.user_id THEN now() ELSE identity_push_devices.created_at END,updated_at=now()",user,token,platform,hash);}
    public void queue(){db.update("""
        INSERT INTO identity_push_deliveries(notification_id,device_id)
        SELECT n.id,d.id FROM notifications n JOIN identity_push_devices d ON d.user_id=n.user_id AND d.enabled
        JOIN account_sessions s ON s.user_id=d.user_id AND s.token_hash=d.session_hash AND s.expires_at>now()
        JOIN users u ON u.id=n.user_id AND u.status='ACTIVE'
        WHERE n.created_at>=d.created_at AND n.created_at>now()-interval '1 day'
         AND n.notification_type IN ('SCHEDULE_UPDATE','CALL_INCOMING') AND jsonb_exists(n.payload,'expiresAt')
         AND (n.payload->>'expiresAt')::timestamptz>now()
        ON CONFLICT DO NOTHING
        """);}
    public List<Long> candidates(String status){return db.queryForList("SELECT id FROM identity_push_deliveries WHERE status=? AND next_attempt_at<=now() ORDER BY id LIMIT 10",Long.class,status);}
    public List<Map<String,Object>> lock(long id,String status){return db.queryForList("""
        SELECT x.*,d.token,d.user_id,n.title,n.content,n.notification_type,n.payload::text AS payload,
         ((n.payload->>'expiresAt')::timestamptz<=now() OR NOT d.enabled OR d.user_id<>n.user_id OR u.status<>'ACTIVE'
          OR NOT EXISTS(SELECT 1 FROM account_sessions s WHERE s.user_id=d.user_id AND s.token_hash=d.session_hash AND s.expires_at>now())) AS expired
        FROM identity_push_deliveries x JOIN identity_push_devices d ON d.id=x.device_id
        JOIN notifications n ON n.id=x.notification_id JOIN users u ON u.id=d.user_id
        WHERE x.id=? AND x.status=? FOR UPDATE OF x SKIP LOCKED
        """,id,status);}
    public void result(long id,String status,String ticket,String error,int delay){db.update("UPDATE identity_push_deliveries SET status=?,ticket_id=COALESCE(?,ticket_id),error_code=?,attempts=attempts+1,next_attempt_at=now()+(? * interval '1 second'),updated_at=now() WHERE id=?",status,ticket,error,delay,id);}
    public void disable(long id){db.update("UPDATE identity_push_devices SET enabled=false,updated_at=now() WHERE id=?",id);}
}
