package com.domesticservice.catalog.controller;
import com.domesticservice.catalog.service.RecoveryVoucherService;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/catalog") public class RecoveryVoucherController {
 private final RecoveryVoucherService s;public RecoveryVoucherController(RecoveryVoucherService s){this.s=s;}
 @PostMapping("/internal/recovery-vouchers") public Map<String,Object> issue(@RequestHeader(value="X-Service-Key",required=false)String key,@RequestBody Map<String,Object>b){s.authorize(key);return s.issue(b);}
 @GetMapping("/customer/vouchers") public List<Map<String,Object>> mine(@RequestHeader(value="Authorization",required=false)String token){return s.mine(token);}
}
