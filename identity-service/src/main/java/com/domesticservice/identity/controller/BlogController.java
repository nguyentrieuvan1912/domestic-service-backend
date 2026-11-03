package com.domesticservice.identity.controller;

import com.domesticservice.identity.service.BlogService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/identity")
public class BlogController {
    private final BlogService service;
    public BlogController(BlogService service) {this.service=service;}
    @GetMapping("/blog") public List<Map<String,Object>> list(){return service.list(null,false);}
    @GetMapping("/blog/{slug}") public Map<String,Object> article(@PathVariable String slug){return service.published(slug);}
    @GetMapping("/admin/blog") public List<Map<String,Object>> adminList(@RequestHeader(value="Authorization",required=false) String token){return service.list(token,true);}
    @GetMapping("/admin/blog/{id}") public Map<String,Object> get(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.get(token,id);}
    @PostMapping("/admin/blog") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body){return service.save(token,null,body);}
    @PutMapping("/admin/blog/{id}") public Map<String,Object> save(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.save(token,id,body);}
    @GetMapping("/admin/blog/media/capabilities") public Map<String,Object> capabilities(@RequestHeader(value="Authorization",required=false) String token){return service.capabilities(token);}
    @PostMapping("/admin/blog/media/signature") public Map<String,Object> sign(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body){return service.sign(token,body);}
    @PostMapping("/admin/blog/media/{id}/complete") public Map<String,Object> complete(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.complete(token,id);}
}
