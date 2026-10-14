package com.domesticservice.identity.controller;

import com.domesticservice.identity.application.*;
import com.domesticservice.identity.dto.CustomerRequests.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
public class CustomerDataController {
    private final AccountService accounts;
    private final CustomerDataService data;
    public CustomerDataController(AccountService accounts,CustomerDataService data) { this.accounts=accounts; this.data=data; }
    @GetMapping("/customer/addresses")
    public List<Map<String,Object>> addresses(@RequestHeader(value="Authorization",required=false) String token) { return data.addresses(accounts.authenticate(token,false)); }
    @GetMapping("/customer/addresses/{id}")
    public Map<String,Object> address(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { return data.address(accounts.authenticate(token,false),id); }
    @PostMapping("/customer/addresses") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> add(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody Address body) { return data.saveAddress(accounts.authenticate(token,false),null,body); }
    @PutMapping("/customer/addresses/{id}")
    public Map<String,Object> edit(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@Valid @RequestBody Address body) { return data.saveAddress(accounts.authenticate(token,false),id,body); }
    @PatchMapping("/customer/addresses/{id}/default")
    public Map<String,Object> defaultAddress(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { return data.setDefault(accounts.authenticate(token,false),id); }
    @DeleteMapping("/customer/addresses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { data.deleteAddress(accounts.authenticate(token,false),id); }
    @GetMapping("/notifications")
    public List<Map<String,Object>> notifications(@RequestHeader(value="Authorization",required=false) String token) { return data.notifications(accounts.authenticate(token,false)); }
    @GetMapping("/notifications/{id}")
    public Map<String,Object> notification(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { return data.notification(accounts.authenticate(token,false),id); }
    @PatchMapping("/notifications/read-all")
    public Map<String,Integer> readAll(@RequestHeader(value="Authorization",required=false) String token) { return data.markRead(accounts.authenticate(token,false),null); }
    @PatchMapping("/notifications/{id}/read")
    public Map<String,Integer> read(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { return data.markRead(accounts.authenticate(token,false),id); }
    @PostMapping("/support/requests") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> contact(@Valid @RequestBody Contact body) { return data.contact(body); }
}
