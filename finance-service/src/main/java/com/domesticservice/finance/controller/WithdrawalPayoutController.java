package com.domesticservice.finance.controller;

import com.domesticservice.finance.service.WithdrawalPayoutService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class WithdrawalPayoutController {
    private final WithdrawalPayoutService service;
    public WithdrawalPayoutController(WithdrawalPayoutService service){this.service=service;}
    @GetMapping("/withdrawal-capabilities")
    public Map<String,Object> capabilities(@RequestHeader(value="Authorization",required=false) String token){return service.capabilities(token);}
    @GetMapping("/payout-banks")
    public List<Map<String,Object>> banks(@RequestHeader(value="Authorization",required=false) String token){return service.banks(token);}
    @GetMapping("/admin/payouts/connection")
    public Map<String,Object> connection(@RequestHeader(value="Authorization",required=false) String token){return service.connection(token);}
    @PostMapping("/admin/withdrawals/{id}/payout")
    public Map<String,Object> dispatch(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.dispatch(token,id,Boolean.TRUE.equals(body.get("confirmed")));}
    @PostMapping({"/admin/withdrawals/{id}/refresh","/staff/me/withdrawals/{id}/refresh"})
    public Map<String,Object> refresh(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.refresh(token,id);}
}
