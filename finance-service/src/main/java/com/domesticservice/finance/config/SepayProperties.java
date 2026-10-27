package com.domesticservice.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payments.sepay")
public record SepayProperties(boolean enabled, String apiToken, String webhookSecret, String bankAccountId,
    String sourceAccount, String sourceName, String tkp, String publicUrl) {
    public boolean ready() {
        return enabled && apiToken != null && !apiToken.isBlank() && webhookSecret != null && webhookSecret.length() >= 32
            && bankAccountId != null && bankAccountId.matches("[0-9a-fA-F-]{36}")
            && sourceAccount != null && sourceAccount.matches("[0-9]{6,30}")
            && sourceName != null && !sourceName.isBlank() && (tkp == null || tkp.isBlank() || tkp.matches("TKP[A-Z0-9]{3}"));
    }
}
