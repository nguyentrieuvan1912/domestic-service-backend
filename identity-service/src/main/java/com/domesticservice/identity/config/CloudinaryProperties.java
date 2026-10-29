package com.domesticservice.identity.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("community.cloudinary")
public record CloudinaryProperties(String cloudName,String apiKey,String apiSecret) {
    public boolean ready(){return cloudName!=null && cloudName.matches("[A-Za-z0-9_-]+") && apiKey!=null && !apiKey.isBlank() && apiSecret!=null && !apiSecret.isBlank();}
}
