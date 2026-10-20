package com.domesticservice.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payments.payos")
public record PayosProperties(boolean enabled, String clientId, String apiKey, String checksumKey,
                              String accountNumber, String returnUrl, String cancelUrl) {
    public boolean ready() {
        return enabled && present(clientId) && present(apiKey) && present(checksumKey)
            && present(accountNumber) && present(returnUrl) && present(cancelUrl);
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
