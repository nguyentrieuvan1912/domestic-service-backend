package com.domesticservice.identity.dto;

import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}
    public record Register(@NotBlank @Size(max=160) String fullName,
                           @NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})") String phone,
                           @NotBlank @Size(max=100) String verificationToken,
                           @NotBlank @Size(min=8,max=72) String password) {}
    public record EmailStart(@NotBlank @Email @Size(max=254) String email) {}
    public record EmailVerify(@NotBlank @Size(max=36) String challengeId,
                              @NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record CustomerProfile(@NotBlank @Size(max=160) String fullName,
                                  @NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})") String phone) {}
    public record StaffProfile(@NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})") String phone,
                               @Size(max=2000) String bio) {}
    public record Login(@NotBlank @Size(max=254) String identifier,
                        @NotBlank @Size(max=72) String password) {}
    public record Status(@NotBlank @Pattern(regexp="ACTIVE|INACTIVE|LOCKED") String status,
                         @Size(max=1000) String statusNote) {}
    public record Provision(@NotBlank @Size(min=8,max=72) String password,
                            @NotBlank @Pattern(regexp="INTERN|STANDARD|PROFESSIONAL") String rank) {}
    public record StaffUpdate(@NotBlank @Size(max=160) String fullName,
                              @NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})") String phone,
                              @NotBlank @Pattern(regexp="ACTIVE|INACTIVE|LOCKED") String status,
                              @Size(max=1000) String statusNote,
                              @NotBlank @Pattern(regexp="INTERN|STANDARD|PROFESSIONAL") String rank) {}
    public record StaffCreate(@NotBlank @Size(max=160)String fullName,@NotBlank @Pattern(regexp="(?:0[0-9]{9}|\\+84[0-9]{9})")String phone,
                              @NotBlank @Email @Size(max=254)String email,@NotBlank @Size(min=8,max=72)String password,
                              @NotBlank @Size(max=30)String identityCard,@NotBlank @Pattern(regexp="INTERN|STANDARD|PROFESSIONAL")String rank) {}
}
