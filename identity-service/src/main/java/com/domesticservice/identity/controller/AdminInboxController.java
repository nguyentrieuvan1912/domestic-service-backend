package com.domesticservice.identity.controller;
import java.util.*;
import com.domesticservice.identity.service.AdminInboxService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/identity/admin/inbox")
public class AdminInboxController {
 private final AdminInboxService service;
 public AdminInboxController(AdminInboxService service){this.service=service;}
 @GetMapping public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String token,@RequestParam(required=false) Long before,@RequestParam(defaultValue="all") String filter){return service.list(token,before,filter);}
 @PostMapping("/sync") public Map<String,Object> sync(@RequestHeader(value="Authorization",required=false) String token){return service.sync(token);}
 @PatchMapping("/{id}/read") public Map<String,Object> read(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.read(token,id);}
 @PatchMapping("/read-all") public Map<String,Object> readAll(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Long> body){return service.readAll(token,body.getOrDefault("throughId",0L));}
}
