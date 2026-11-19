package com.domesticservice.booking.controller;
import com.domesticservice.booking.service.CustomerPaymentBridge;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/booking/internal/bookings")
public class CustomerPaymentBridgeController {
 private final CustomerPaymentBridge bridge;
 public CustomerPaymentBridgeController(CustomerPaymentBridge bridge){this.bridge=bridge;}
 @GetMapping("/{id}") public Map<String,Object> snapshot(@RequestHeader(value="X-Service-Key",required=false) String key,@PathVariable long id){bridge.authorize(key);return bridge.snapshot(id);}
 @PostMapping("/{id}/reconcile-payment") public void reconcile(@RequestHeader(value="X-Service-Key",required=false) String key,@PathVariable long id){bridge.authorize(key);bridge.reconcile(id);}
 @PutMapping("/{id}/payment-method") public Map<String,Object> changeMethod(@RequestHeader(value="X-Service-Key",required=false) String key,@PathVariable long id,@RequestBody MethodChange body){bridge.authorize(key);return bridge.changeMethod(id,body.customerId(),body.method());}
 public record MethodChange(long customerId, String method) {}
}
