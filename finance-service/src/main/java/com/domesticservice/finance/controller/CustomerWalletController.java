package com.domesticservice.finance.controller;
import com.domesticservice.finance.service.*;
import com.domesticservice.finance.dto.StaffTopupRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/finance")
public class CustomerWalletController {
 private final CustomerWalletService wallet; private final CustomerTopupService topups;
 public CustomerWalletController(CustomerWalletService wallet,CustomerTopupService topups){this.wallet=wallet;this.topups=topups;}
 @GetMapping("/customer/me/wallet") public Map<String,Object> wallet(@RequestHeader(value="Authorization",required=false) String token){return wallet.wallet(token);}
 @GetMapping("/customer/me/topups") public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String token){return topups.list(token,false);}
 @PostMapping("/customer/me/topups") public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody StaffTopupRequest body){return topups.create(token,body);}
 @PostMapping("/customer/me/topups/{id}/refresh") public Map<String,Object> refresh(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return topups.refresh(token,id,false);}
 @PostMapping("/customer/bookings/{id}/pay") public Map<String,Object> pay(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return wallet.pay(token,id);}
 @GetMapping("/internal/customer-payments/{id}") public Map<String,Object> status(@RequestHeader(value="X-Service-Key",required=false) String key,@PathVariable long id){wallet.authorize(key);return wallet.status(id);}
}
