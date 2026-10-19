package com.domesticservice.identity.config;

import com.domesticservice.identity.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Opt-in, first active admin only. Credentials are never returned or logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AccountService accounts;

    private final String email, phone, password;

    public AdminBootstrap(AccountService accounts, @Value("${identity.bootstrap.email:}") String email, @Value("${identity.bootstrap.phone:}") String phone, @Value("${identity.bootstrap.password:}") String password) {
        this.accounts = accounts;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!email.isBlank() && !phone.isBlank() && !password.isBlank()) {
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || !phone.matches("(?:0[0-9]{9}|\\+84[0-9]{9})"))
                throw new IllegalArgumentException("Invalid bootstrap admin email or phone");
            accounts.bootstrap(email, phone, password);
        }
    }
}
