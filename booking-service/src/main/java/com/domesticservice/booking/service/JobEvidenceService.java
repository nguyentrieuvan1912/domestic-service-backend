package com.domesticservice.booking.service;

import com.domesticservice.booking.repository.JobEvidenceRepository;
import com.domesticservice.common.web.ServiceHttpClient;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.domesticservice.booking.service.BookingOperationsService.*;

@Service
public class JobEvidenceService {
 private final BookingOperationsService ops;private final JobEvidenceRepository repository;private final ObjectMapper json;
 public JobEvidenceService(BookingOperationsService ops,JobEvidenceRepository repository,ObjectMapper json){this.ops=ops;this.repository=repository;this.json=json;}
 private Map<String,Object> authorize(String token,long id){
  var actor=ops.actor(token,"CUSTOMER","STAFF","ADMIN");var book=repository.booking(id,false);
  if(book==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn.");
  long user=ServiceHttpClient.userId(actor);
  if("CUSTOMER".equals(actor.get("role")) && ((Number)book.get("customer_id")).longValue()!=user || "STAFF".equals(actor.get("role")) && repository.assignment(id,user,false).isEmpty())throw error(HttpStatus.NOT_FOUND,"Không tìm thấy minh chứng của tài khoản này.");
  return actor;
 }
 public List<Map<String,Object>> list(String token,long id){var actor=authorize(token,id);return repository.list(id,"STAFF".equals(actor.get("role"))?ServiceHttpClient.userId(actor):null);}
 public Map<String,Object> image(String token,long booking,long id){var actor=authorize(token,booking);var image=repository.image(booking,id);if(image==null || "STAFF".equals(actor.get("role")) && ((Number)image.get("staff_id")).longValue()!=ServiceHttpClient.userId(actor))throw error(HttpStatus.NOT_FOUND,"Không tìm thấy ảnh minh chứng.");return image;}
 public record ValidImage(byte[] bytes,byte[] thumbnail,String mime,String hash,int width,int height){}
 static ValidImage validate(String encoded){
  if(encoded==null || encoded.isBlank() || encoded.length()>2796204)throw error(HttpStatus.BAD_REQUEST,"Ảnh minh chứng phải là JPEG/PNG tối đa 2MB.");
  try {
   byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length==0 || bytes.length>2097152)throw new IOException();
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();
    try {
     reader.setInput(input,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
     if(!List.of("jpeg","jpg","png").contains(format))throw new IOException();
     int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1 || height<1 || (long)width*height>16000000)throw new IOException();
     var original=reader.read(0);double scale=Math.min(1,640.0/Math.max(width,height));
     var preview=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
     var graphics=preview.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,preview.getWidth(),preview.getHeight());graphics.drawImage(original,0,0,preview.getWidth(),preview.getHeight(),null);graphics.dispose();
     var output=new ByteArrayOutputStream();ImageIO.write(preview,"jpeg",output);
     return new ValidImage(bytes,output.toByteArray(),format.equals("png")?"image/png":"image/jpeg",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),width,height);
    }finally{reader.dispose();}
   }
  }catch(Exception ex){throw error(HttpStatus.BAD_REQUEST,"Không đọc được ảnh. Vui lòng dùng JPEG/PNG tối đa 2MB, độ phân giải tối đa 16 triệu điểm ảnh.");}
 }
 @Transactional public Map<String,Object> upload(String token,long id,Map<String,Object> body){
  var actor=ops.actor(token,"STAFF");long staff=ServiceHttpClient.userId(actor);
  String stage=text(body,"stage",10,true),key=text(body,"requestKey",120,true);
  if(!List.of("BEFORE","AFTER").contains(stage) || !key.matches("[A-Za-z0-9_-]+"))throw error(HttpStatus.BAD_REQUEST,"Loại ảnh hoặc mã yêu cầu không hợp lệ.");
  var image=validate(Objects.toString(body.get("imageBase64"),""));
  ops.lockStaff(staff);var book=repository.booking(id,true);
  if(book==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn.");
  var assignments=repository.assignment(id,staff,true);if(assignments.isEmpty())throw error(HttpStatus.NOT_FOUND,"Bạn chưa nhận ca này.");
  var assignment=assignments.getFirst();long assignmentId=((Number)assignment.get("id")).longValue();
  var previous=repository.previous(id,staff,key);
  if(!previous.isEmpty()){
   if(!image.hash().equals(previous.getFirst().get("sha256")) || !stage.equals(previous.getFirst().get("stage")))throw error(HttpStatus.CONFLICT,"Mã gửi ảnh đã được dùng cho ảnh khác.");
   return Map.of("id",previous.getFirst().get("id"),"saved",true);
  }
  if(!List.of("CONFIRMED","ACCEPTED","STAFF_ASSIGNED","IN_PROGRESS").contains(book.get("status")) || !"ACCEPTED".equals(assignment.get("status")) || !(stage.equals("BEFORE")?List.of("EN_ROUTE","IN_PROGRESS"):List.of("IN_PROGRESS")).contains(assignment.get("execution_status")))throw error(HttpStatus.CONFLICT,"Chỉ được gửi ảnh trước khi đến nơi/bắt đầu và ảnh sau khi đang thực hiện ca; đơn kết thúc chỉ được xem.");
  if(repository.count(assignmentId,stage)>=5)throw error(HttpStatus.CONFLICT,"Mỗi ca được lưu tối đa 5 ảnh trước và 5 ảnh sau; vui lòng kiểm tra các ảnh đã gửi.");
  if(repository.duplicate(id,staff,image.hash()))throw error(HttpStatus.CONFLICT,"Ảnh này đã được gửi. Cần ảnh trước và sau khác nhau.");
  var conversation=repository.conversation(id,staff);if(!"ACTIVE".equals(conversation.get("status")))throw error(HttpStatus.CONFLICT,"Hội thoại của ca đã đóng.");
  long conversationId=((Number)conversation.get("id")).longValue();
  String caption="Ảnh minh chứng "+(stage.equals("BEFORE")?"TRƯỚC":"SAU")+" thực hiện dịch vụ · "+book.get("booking_code");
  long message=repository.message(conversationId,staff,caption,"data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(image.thumbnail()),key);
  long evidence=repository.save(id,assignmentId,staff,stage,key,image.mime(),image.bytes(),image.thumbnail(),image.hash(),image.width(),image.height(),message);
  repository.audit(id,staff,Objects.toString(book.get("status")),evidence,stage,json.writeValueAsString(Map.of("messageId",message,"conversationId",conversationId,"recipientId",book.get("customer_id"),"senderId",staff,"senderName",Objects.toString(actor.get("fullName"),"Nhân viên"))));
  return Map.of("id",evidence,"messageId",message,"conversationId",conversationId,"saved",true);
 }
}
