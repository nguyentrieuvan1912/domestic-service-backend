package com.domesticservice.identity.dto;

import java.time.Instant;

public final class AccountResponse {

    private AccountResponse() {
    }

    public record User(long id, String fullName, String phone, String email, String role, String status) {
    }

    public record Session(String accessToken, Instant expiresAt, User user) {
    }
}
