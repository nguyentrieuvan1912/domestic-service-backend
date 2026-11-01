package com.domesticservice.identity.controller;

import com.domesticservice.identity.service.AccountService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/candidates")
public class PublicCandidateController {

    private final AccountService accounts;

    public PublicCandidateController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        return accounts.applyCandidate(body);
    }
}
