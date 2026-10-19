package com.domesticservice.identity.dto;

import java.time.Instant;

public final class RegistrationResponse {

    private RegistrationResponse() {
    }

    public record Challenge(String challengeId, String email, Instant expiresAt, Instant resendAt) {
    }

    public record Verified(String verificationToken, String email, Instant expiresAt) {
    }
}
