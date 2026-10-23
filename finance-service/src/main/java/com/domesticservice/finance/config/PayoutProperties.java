package com.domesticservice.finance.config;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payments.payout")
public record PayoutProperties(boolean enabled, String clientId, String apiKey, String checksumKey,
    String sourceAccount, String sourceName, String bankBins, long minimum, long maximum) {
    public Set<String> allowedBins() {
        if (bankBins == null || bankBins.isBlank()) return Set.of();
        var bins = new HashSet<>(Arrays.asList(bankBins.replace(" ", "").split(",")));
        return bins.stream().allMatch(b -> b.matches("[0-9]{6}")) ? Set.copyOf(bins) : Set.of();
    }
    public boolean ready() {
        return enabled && List.of(Objects.toString(clientId,""),Objects.toString(apiKey,""),
            Objects.toString(checksumKey,""),Objects.toString(sourceAccount,""),Objects.toString(sourceName,"")).stream().noneMatch(String::isBlank)
            && !allowedBins().isEmpty() && minimum > 0 && maximum >= minimum && maximum <= 1_000_000_000L;
    }
}
