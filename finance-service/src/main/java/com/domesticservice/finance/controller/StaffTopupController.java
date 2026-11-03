package com.domesticservice.finance.controller;

import com.domesticservice.finance.dto.StaffTopupRequest;
import com.domesticservice.finance.service.StaffTopupService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class StaffTopupController {
    private final StaffTopupService service;
    private final com.domesticservice.finance.service.CustomerTopupService customerTopups;
    public StaffTopupController(StaffTopupService service, com.domesticservice.finance.service.CustomerTopupService customerTopups){this.service=service;this.customerTopups=customerTopups;}
    @GetMapping("/payment-capabilities")
    public Map<String,Object> capabilities(@RequestHeader(value="Authorization",required=false) String token){return service.readiness(token);}
    @GetMapping("/staff/me/topups")
    public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String token){return service.list(token,false);}
    @PostMapping("/staff/me/topups")
    public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody StaffTopupRequest request){return service.create(token,request);}
    @PostMapping("/staff/me/topups/{id}/refresh")
    public Map<String,Object> refresh(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.refresh(token,id,false);}
    @GetMapping("/admin/topups")
    public List<Map<String,Object>> adminList(@RequestHeader(value="Authorization",required=false) String token){return service.list(token,true);}
    @PostMapping("/admin/topups/{id}/refresh")
    public Map<String,Object> adminRefresh(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.refresh(token,id,true);}
    @PostMapping("/webhooks/payos")
    public Map<String,Object> webhook(@RequestBody Map<String,Object> body){service.webhook(body);customerTopups.webhook(body);return Map.of("success",true);}
    @GetMapping(value="/payments/return",produces="text/plain;charset=UTF-8")
    public String paymentReturn(){return "Đã quay lại từ trang thanh toán. Hãy mở ứng dụng và kiểm tra giao dịch. Trang này không xác nhận đã nhận tiền.";}
}
