package com.domesticservice.identity.controller;
import com.domesticservice.identity.service.PushService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/identity/push")
public class PushController {
    private final PushService service;
    public PushController(PushService service){this.service=service;}
    @PostMapping("/devices") public Map<String,Object> register(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body){return service.register(token,body);}
}
