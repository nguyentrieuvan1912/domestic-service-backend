package com.domesticservice.finance.controller;
import com.domesticservice.finance.service.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance/internal/job-settlement")
public class AutomaticJobSettlementController {
 private final AutomaticJobSettlement jobs;private final CustomerWalletService auth;
 public AutomaticJobSettlementController(AutomaticJobSettlement jobs,CustomerWalletService auth){this.jobs=jobs;this.auth=auth;}
 @PostMapping("/reserve/{staff}") public Map<String,Object> reserve(@RequestHeader(value="X-Service-Key",required=false)String key,@PathVariable long staff,@RequestBody Map<String,Object> book){auth.authorize(key);return jobs.reserve(book,staff);}
 @PostMapping("/reconcile") public Map<String,Object> reconcile(@RequestHeader(value="X-Service-Key",required=false)String key,@RequestBody Map<String,Object> book){auth.authorize(key);return jobs.reconcile(book);}
}
