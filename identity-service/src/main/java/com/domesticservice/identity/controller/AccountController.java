package com.domesticservice.identity.controller;

import com.domesticservice.identity.application.AccountService;
import com.domesticservice.identity.application.AccountService.*;
import com.domesticservice.identity.dto.AccountRequests.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts=accounts; }
    @GetMapping("/staff-directory") public List<Map<String,Object>> directory(){return accounts.publicStaff();}
    @PatchMapping("/staff/status") public Map<String,Object> online(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object> body){
        if(!(body.get("isOnline") instanceof Boolean))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái trực tuyến không hợp lệ.");
        return accounts.staffOnline(accounts.authenticate(token,false),(Boolean)body.get("isOnline"));
    }
    @PostMapping("/auth/register/email")
    public com.domesticservice.identity.application.RegistrationVerificationService.Challenge email(@Valid @RequestBody EmailStart body) { return accounts.sendRegistrationCode(body); }
    @PostMapping("/auth/register/verify")
    public com.domesticservice.identity.application.RegistrationVerificationService.Verified verify(@Valid @RequestBody EmailVerify body) { return accounts.verifyRegistrationCode(body); }
    @GetMapping("/auth/profile")
    public Map<String,Object> profile(@RequestHeader(value="Authorization",required=false) String token) { return accounts.profile(accounts.authenticate(token,false)); }
    @PatchMapping("/customer/profile")
    public Map<String,Object> updateProfile(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody CustomerProfile body) { return accounts.updateCustomerProfile(accounts.authenticate(token,false),body); }
    @PatchMapping("/staff/profile")
    public Map<String,Object> updateStaffProfile(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody StaffProfile body) { return accounts.updateStaffProfile(accounts.authenticate(token,false),body); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody Register body) { return accounts.register(body); }
    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody Login body) { return accounts.login(body); }
    @GetMapping("/auth/me")
    public User me(@RequestHeader(value="Authorization",required=false) String token) { return accounts.authenticate(token,false); }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value="Authorization",required=false) String token) { accounts.logout(token); }
    @GetMapping("/admin/customers")
    public List<Map<String,Object>> customers(@RequestHeader(value="Authorization",required=false) String token) { accounts.authenticate(token,true); return accounts.customers(); }
    @PatchMapping("/admin/customers/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@Valid @RequestBody Status body) { accounts.authenticate(token,true); accounts.status(id,"CUSTOMER",body); }
    @GetMapping("/admin/candidates")
    public List<Map<String,Object>> candidates(@RequestHeader(value="Authorization",required=false) String token) { accounts.authenticate(token,true); return accounts.candidates(); }
    @PostMapping("/admin/candidates") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body) { accounts.authenticate(token,true); return accounts.newCandidate(body); }
    @PatchMapping("/admin/candidates/{id}")
    public Map<String,Object> advance(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body) { accounts.authenticate(token,true); return accounts.advance(id,body); }
    @PostMapping("/admin/candidates/{id}/staff-account") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> provision(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@Valid @RequestBody Provision body) { User admin=accounts.authenticate(token,true); return accounts.provision(id,body,admin.id()); }
    @GetMapping("/admin/staff")
    public List<Map<String,Object>> staff(@RequestHeader(value="Authorization",required=false) String token) { accounts.authenticate(token,true); return accounts.staff(); }
    @PostMapping("/admin/staff") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createStaff(@RequestHeader(value="Authorization",required=false)String token,@Valid @RequestBody StaffCreate body){return accounts.createStaff(accounts.authenticate(token,true),body);}
    @PatchMapping("/admin/staff/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStaff(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@Valid @RequestBody StaffUpdate body) { accounts.authenticate(token,true); accounts.updateStaff(id,body); }
}
