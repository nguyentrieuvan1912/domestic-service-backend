package com.domesticservice.identity.controller;

import com.domesticservice.identity.service.AccountService;
import com.domesticservice.identity.service.PartnerCaptchaService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/candidates")
public class PublicCandidateController {

    private final AccountService accounts;
    private final PartnerCaptchaService captcha;

    public PublicCandidateController(AccountService accounts, PartnerCaptchaService captcha) {
        this.accounts = accounts;
        this.captcha = captcha;
    }

    @GetMapping("/captcha")
    public org.springframework.http.ResponseEntity<PartnerCaptchaService.Challenge> challenge() {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(captcha.issue());
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        captcha.verify(body.get("captchaId"), body.get("captchaCode"));
        return accounts.applyCandidate(body);
    }
}
