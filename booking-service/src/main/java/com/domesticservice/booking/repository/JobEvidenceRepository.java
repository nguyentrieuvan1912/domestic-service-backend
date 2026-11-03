package com.domesticservice.booking.repository;

import com.domesticservice.booking.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JobEvidenceRepository {
 private final NativeQueryExecutor db;
 public JobEvidenceRepository(NativeQueryExecutor db){this.db=db;}
 public Map<String,Object> booking(long id,boolean lock){var rows=db.queryForList("SELECT * FROM bookings WHERE id=?"+(lock?" FOR UPDATE":""),id);return rows.isEmpty()?null:rows.getFirst();}
 public List<Map<String,Object>> assignment(long id,long staff,boolean lock){return db.queryForList("SELECT * FROM booking_assignments WHERE booking_id=? AND staff_id=? AND status IN ('ACCEPTED','COMPLETED') ORDER BY id DESC"+(lock?" FOR UPDATE":""),id,staff);}
 public List<Map<String,Object>> previous(long id,long staff,String key){return db.queryForList("SELECT id,stage,sha256 FROM booking_job_evidence WHERE booking_id=? AND staff_id=? AND request_key=?",id,staff,key);}
 public boolean duplicate(long id,long staff,String hash){return db.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_job_evidence WHERE booking_id=? AND staff_id=? AND sha256=?)",Boolean.class,id,staff,hash);}
 public long count(long assignment){return db.queryForObject("SELECT count(*) FROM booking_job_evidence WHERE assignment_id=?",Long.class,assignment);}
 public long count(long assignment,String stage){return db.queryForObject("SELECT count(*) FROM booking_job_evidence WHERE assignment_id=? AND stage=?",Long.class,assignment,stage);}
 public boolean complete(long assignment){return db.queryForObject("SELECT count(DISTINCT stage)=2 FROM booking_job_evidence WHERE assignment_id=?",Boolean.class,assignment);}
 public Map<String,Object> conversation(long id,long staff){return db.queryForMap("SELECT * FROM conversations WHERE booking_id=? AND staff_id=? FOR UPDATE",id,staff);}
 public long message(long conversation,long staff,String content,String preview,String key){
  db.update("UPDATE conversations SET customer_trashed_at=NULL,customer_deleted_at=NULL WHERE id=?",conversation);
  return db.insert("INSERT INTO messages(conversation_id,sender_id,sender_type,message_type,content,media_url,client_key) VALUES (?,?,'STAFF','IMAGE',?,?,?)",conversation,staff,content,preview,"evidence:"+key);
 }
 public long save(long id,long assignment,long staff,String stage,String key,String mime,byte[] image,byte[] thumbnail,String hash,int width,int height,long message){return db.insert("INSERT INTO booking_job_evidence(booking_id,assignment_id,staff_id,stage,request_key,mime_type,image_data,thumbnail_data,sha256,width,height,message_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,assignment,staff,stage,key,mime,image,thumbnail,hash,width,height,message);}
 public void audit(long booking,long staff,String state,long evidence,String stage,String payload){
  db.insert("INSERT INTO booking_status_histories(booking_id,old_status,new_status,changed_by,changed_by_type,reason) VALUES (?,?,?,?,'STAFF',?)",booking,state,state,staff,"Đã gửi ảnh minh chứng "+("BEFORE".equals(stage)?"trước":"sau")+" thực hiện dịch vụ #"+evidence);
  db.insert("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload) VALUES ('BOOKING',?,'CHAT_MESSAGE',CAST(? AS jsonb))",booking,payload);
 }
 public List<Map<String,Object>> list(long id,Long staff){return db.queryForList("SELECT id,booking_id AS \"bookingId\",assignment_id AS \"assignmentId\",staff_id AS \"staffId\",stage,mime_type AS \"mimeType\",sha256,width,height,message_id AS \"messageId\",created_at AS \"createdAt\",'data:image/jpeg;base64,'||replace(encode(thumbnail_data,'base64'),chr(10),'') AS \"thumbnailUrl\" FROM booking_job_evidence WHERE booking_id=?"+(staff==null?"":" AND staff_id=?")+" ORDER BY created_at,id",staff==null?new Object[]{id}:new Object[]{id,staff});}
 public Map<String,Object> image(long booking,long id){var rows=db.queryForList("SELECT staff_id,mime_type,image_data,sha256 FROM booking_job_evidence WHERE booking_id=? AND id=?",booking,id);return rows.isEmpty()?null:rows.getFirst();}
}
