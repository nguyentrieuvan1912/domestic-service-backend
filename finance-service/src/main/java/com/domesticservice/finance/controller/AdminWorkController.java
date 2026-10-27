package com.domesticservice.finance.controller;
import java.util.*;
import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.repository.AdminWorkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance/admin/work-items")
public class AdminWorkController {
 private final AdminWorkRepository repo;private final ServiceHttpClient identity;
 public AdminWorkController(AdminWorkRepository repo,@Value("${clients.identity.base-url}") String url){this.repo=repo;identity=new ServiceHttpClient(url);}
 @GetMapping public Map<String,Object> get(@RequestHeader(value="Authorization",required=false) String token){identity.authenticate(token,"ADMIN");return repo.snapshot();}
}
