package com.domesticservice.booking.controller;
import com.domesticservice.booking.service.JobEvidenceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/booking/bookings/{bookingId}/evidence")
public class JobEvidenceController {
 private final JobEvidenceService service;
 public JobEvidenceController(JobEvidenceService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long bookingId){return service.list(token,bookingId);}
 @PostMapping public Map<String,Object> upload(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long bookingId,@RequestBody Map<String,Object> body){return service.upload(token,bookingId,body);}
 @GetMapping("/{id}/original") public ResponseEntity<Map<String,Object>> original(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long bookingId,@PathVariable long id){var image=service.image(token,bookingId,id);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(Map.of("dataUrl","data:"+image.get("mime_type")+";base64,"+Base64.getEncoder().encodeToString((byte[])image.get("image_data")),"sha256",image.get("sha256")));}
 @GetMapping("/{id}/image") public ResponseEntity<byte[]> image(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long bookingId,@PathVariable long id){var image=service.image(token,bookingId,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.get("mime_type").toString())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=\"evidence-"+id+(image.get("mime_type").equals("image/png")?".png":".jpg")+"\"").body((byte[])image.get("image_data"));}
}
