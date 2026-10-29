package com.domesticservice.finance.controller;

import com.domesticservice.finance.service.SepayDisbursementService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class SepayDisbursementController {
    private final SepayDisbursementService service;
    public SepayDisbursementController(SepayDisbursementService service){this.service=service;}
    @PostMapping("/admin/withdrawals/{id}/manual-transfer")
    public Map<String,Object> prepare(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return service.prepare(token,id);}
    @GetMapping("/admin/withdrawals/{id}/manual-transfer")
    public Map<String,Object> detail(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return service.detail(token,id);}
    @PostMapping("/admin/withdrawals/{id}/manual-transfer/confirm")
    public Map<String,Object> confirm(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.confirm(token,id,body);}
    @PostMapping(value="/webhooks/sepay",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> webhook(@RequestBody byte[] body,@RequestHeader(value="X-SePay-Timestamp",required=false)String timestamp,
        @RequestHeader(value="X-SePay-Signature",required=false)String signature){service.webhook(body,timestamp,signature);return Map.of("success",true);}
}
