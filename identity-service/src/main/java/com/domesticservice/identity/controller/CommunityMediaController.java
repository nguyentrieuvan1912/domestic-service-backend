package com.domesticservice.identity.controller;
import com.domesticservice.identity.service.CommunityMediaService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/identity/community/media")
public class CommunityMediaController {
    private final CommunityMediaService service;
    public CommunityMediaController(CommunityMediaService service){this.service=service;}
    @GetMapping("/capabilities") public Map<String,Object> capabilities(){return service.capabilities();}
    @PostMapping("/signature") public Map<String,Object> sign(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body){return service.sign(token,body);}
    @PostMapping("/{id}/complete") public Map<String,Object> complete(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.complete(token,id);}
}
