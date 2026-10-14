package com.domesticservice.finance.controller;
import com.domesticservice.finance.application.FinanceOperationsService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance")
public class FinanceOperationsController {
    private final FinanceOperationsService ops;public FinanceOperationsController(FinanceOperationsService ops){this.ops=ops;}
    @GetMapping("/staff/me/wallet")public Map<String,Object>wallet(@RequestHeader(value="Authorization",required=false)String token){return ops.wallet(token,null);}
    @GetMapping("/admin/staff/{id}/wallet")public Map<String,Object>wallet(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return ops.wallet(token,id);}
    @GetMapping("/staff/me/bank-accounts")public List<Map<String,Object>>banks(@RequestHeader(value="Authorization",required=false)String token){return ops.banks(token);}
    @PostMapping("/staff/me/bank-accounts") @ResponseStatus(HttpStatus.CREATED)public Map<String,Object>bank(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return ops.saveBank(token,b);}
    @PostMapping("/staff/me/withdrawals") @ResponseStatus(HttpStatus.CREATED)public Map<String,Object>withdraw(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return ops.withdraw(token,b);}
    @GetMapping("/admin/bank-accounts")public List<Map<String,Object>>adminBanks(@RequestHeader(value="Authorization",required=false)String token){return ops.adminBanks(token);}
    @PatchMapping("/admin/bank-accounts/{id}")public Map<String,Object>verify(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){return ops.verifyBank(token,id,b);}
    @GetMapping("/admin/withdrawals")public List<Map<String,Object>>withdrawals(@RequestHeader(value="Authorization",required=false)String token){return ops.withdrawals(token);}
    @PatchMapping("/admin/withdrawals/{id}")public Map<String,Object>review(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){return ops.reviewWithdrawal(token,id,b);}
    @GetMapping("/admin/transactions")public List<Map<String,Object>>transactions(@RequestHeader(value="Authorization",required=false)String token){return ops.transactions(token);}
    @PatchMapping("/admin/transactions/{type}/{id}")public Map<String,Object>note(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String type,@PathVariable long id,@RequestBody Map<String,Object>b){return ops.note(token,type,id,b);}
    @GetMapping("/invoices")public List<Map<String,Object>>invoices(@RequestHeader(value="Authorization",required=false)String token){return ops.invoices(token);}
    @PostMapping("/bookings/{id}/payment")public Map<String,Object>payment(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return ops.paymentProvider(token,id);}
    @GetMapping("/bookings/{id}/payment-status")public Map<String,Object>paymentStatus(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){return ops.paymentStatus(token,id);}
    @GetMapping("/admin/policy")public Map<String,Object>policy(@RequestHeader(value="Authorization",required=false)String token){return ops.policy(token);}
    @PutMapping("/admin/policy")public Map<String,Object>policy(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return ops.savePolicy(token,b);}
    @PostMapping("/admin/bookings/{id}/cash-receipt")public Map<String,Object>cash(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){var result=ops.cashReceipt(token,id,b);ops.syncPayment(token,id);return result;}
    @PostMapping("/admin/bookings/{id}/settle")public Map<String,Object>settle(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){var result=ops.settle(token,id);ops.syncPayment(token,id);return result;}
    @GetMapping("/admin/adjustments")public List<Map<String,Object>>adjustments(@RequestHeader(value="Authorization",required=false)String token){return ops.adjustments(token);}
    @PostMapping("/admin/staff/{id}/rewards")public Map<String,Object>reward(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){return ops.requestReward(token,id,b);}
    @PostMapping("/admin/staff/{id}/penalties")public Map<String,Object>penalty(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){return ops.requestPenalty(token,id,b);}
    @PostMapping("/admin/adjustments/{kind}/{id}/apply")public Map<String,Object>apply(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String kind,@PathVariable long id){return ops.applyAdjustment(token,kind,id);}
}
