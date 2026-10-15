package com.domesticservice.identity.dto;

import jakarta.validation.constraints.*;

public final class CustomerRequests {
    private CustomerRequests() {}
    // Ownership always comes from the authenticated session, never from the request.
    public record Address(@NotBlank @Size(max=100) String title,
                          @NotBlank @Size(max=160) String recipientName,
                          @NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})") String recipientPhone,
                          @NotBlank @Size(max=2000) String streetAddress,
                          @Size(max=100) String ward,
                          @Size(max=100) String district,
                          @NotBlank @Size(max=100) String city,
                          @Size(max=2000) String note,
                          Boolean isDefault) {}
    public record Contact(@NotBlank @Size(max=160) String name,
                          @NotBlank @Email @Size(max=254) String email,
                          @NotBlank @Size(max=255) String subject,
                          @NotBlank @Size(max=10000) String message) {}
}
