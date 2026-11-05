package com.domesticservice.finance.controller;
import com.domesticservice.finance.service.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance") public class StaffDisciplineController {
 private final StaffDisciplineService service;private final CustomerWalletService auth;
 public StaffDisciplineController(StaffDisciplineService service,CustomerWalletService auth){this.service=service;this.auth=auth;}
 @GetMapping("/staff/discipline") public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false)String token){return service.list(token);}
 @GetMapping("/admin/discipline") public List<Map<String,Object>> admin(@RequestHeader(value="Authorization",required=false)String token){return service.adminList(token);}
 @PostMapping("/admin/discipline") public Map<String,Object> create(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return service.manual(token,b);}
 @GetMapping("/admin/discipline/policy") public Map<String,Object> policy(@RequestHeader(value="Authorization",required=false)String token){return service.adminPolicy(token);}
 @PutMapping("/admin/discipline/policy") public Map<String,Object> policy(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return service.savePolicy(token,b);}
 @GetMapping("/internal/discipline/policy") public Map<String,Object> internal(@RequestHeader(value="X-Service-Key",required=false)String key){auth.authorize(key);return service.policy();}
 @PostMapping("/internal/discipline/no-show") public Map<String,Object> noShow(@RequestHeader(value="X-Service-Key",required=false)String key,@RequestBody Map<String,Object>b){auth.authorize(key);return service.noShow(b);}
 @GetMapping("/admin/discipline/policy/history") public List<Map<String,Object>> history(@RequestHeader(value="Authorization",required=false)String token){return service.policyHistory(token);}
 @PostMapping("/internal/discipline/refund") public Map<String,Object> refund(@RequestHeader(value="X-Service-Key",required=false)String key,@RequestBody Map<String,Object>b){auth.authorize(key);return service.refundForgotten(b);}
}
