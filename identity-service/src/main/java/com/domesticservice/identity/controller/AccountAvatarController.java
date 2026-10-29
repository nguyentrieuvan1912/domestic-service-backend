package com.domesticservice.identity.controller;

import com.domesticservice.identity.service.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/identity")
public class AccountAvatarController {
    private final AccountService accounts;
    private final AvatarService avatars;
    public AccountAvatarController(AccountService accounts, AvatarService avatars) { this.accounts=accounts; this.avatars=avatars; }
    @PatchMapping("/auth/avatar")
    public Map<String,Object> update(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String,String> body) {
        var user=accounts.authenticate(token,false);
        if ("STAFF".equals(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Ảnh nhân viên phải rõ mặt, chính chủ. Vui lòng liên hệ Admin để thay đổi.");
        if (!body.containsKey("avatar")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiếu trường avatar.");
        avatars.save(user.id(),body.get("avatar"),false);
        return accounts.profile(user);
    }
}
