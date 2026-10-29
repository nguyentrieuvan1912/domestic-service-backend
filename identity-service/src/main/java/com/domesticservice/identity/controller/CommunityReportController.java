package com.domesticservice.identity.controller;
import com.domesticservice.identity.service.CommunityReportService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api/v1/identity")
public class CommunityReportController {
    private final CommunityReportService service;
    public CommunityReportController(CommunityReportService service){this.service=service;}
    @PostMapping("/community/posts/{post}/comments/{comment}/reports") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> report(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long post,@PathVariable long comment,@RequestBody Map<String,Object> body){return service.report(token,post,comment,body);}
    @GetMapping("/admin/community/reports")
    public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String token,@RequestParam(defaultValue="OPEN") String status,@RequestParam(required=false) Long before){return service.list(token,status,before);}
    @PatchMapping("/admin/community/reports/{id}")
    public Map<String,Object> resolve(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.resolve(token,id,body);}
}
