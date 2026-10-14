package com.domesticservice.ai.controller;
import com.domesticservice.ai.application.AiConversationService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/v1/ai-assistant/conversations")
public class AiConversationController {
    private final AiConversationService service;
    public AiConversationController(AiConversationService service){this.service=service;}
    @GetMapping public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false)String token){return service.conversations(token);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)public Map<String,Object>create(@RequestHeader(value="Authorization",required=false)String token){return service.create(token);}
    @GetMapping("/{id}/messages") public List<Map<String,Object>>messages(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return service.messages(token,id);}
    @PostMapping("/{id}/messages")public Map<String,Object>send(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>body){return service.send(token,id,body);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)public void clear(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){service.clear(token,id);}
}
