package com.domesticservice.finance.dto;

import jakarta.validation.constraints.*;

public record StaffTopupRequest(@Min(2000) @Max(1000000) long amount,
                               @NotBlank @Size(max=160) @Pattern(regexp="[A-Za-z0-9_-]+") String requestKey) {}
