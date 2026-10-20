package com.domesticservice.identity.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.domesticservice.identity.service.CommunityService;

@RestController
@RequestMapping("/api/v1/identity")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/community/posts")
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.list(token);
    }

    @GetMapping("/admin/community/posts")
    public List<Map<String, Object>> admin(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.admin(token);
    }

    @PostMapping("/community/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String token, @RequestBody Map<String, Object> b) {
        return service.create(token, b);
    }

    @PostMapping("/community/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> comment(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.comment(token, id, b);
    }

    @PatchMapping("/community/posts/{id}/like")
    public Map<String, Object> like(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.like(token, id, b);
    }

    @PatchMapping("/admin/community/posts/{id}")
    public Map<String, Object> moderate(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.moderate(token, id, b);
    }

    @GetMapping("/community/posts/{id}")
    public Map<String,Object> post(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.post(token,id);}
    @GetMapping("/community/posts/{id}/comments")
    public Map<String,Object> comments(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestParam(required=false) Long before,@RequestParam(required=false) Long after){return service.comments(token,id,before,after);}
    @PatchMapping("/community/posts/{id}/reaction")
    public Map<String,Object> reaction(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> b){return service.react(token,id,b);}
    @PatchMapping("/community/posts/{id}/comments/{commentId}/reaction")
    public Map<String,Object> commentReaction(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@PathVariable long commentId,@RequestBody Map<String,Object> b){return service.commentReaction(token,id,commentId,b);}
}
