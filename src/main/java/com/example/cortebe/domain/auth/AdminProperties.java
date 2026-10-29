package com.example.cortebe.domain.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("admin")
public record AdminProperties(
    List<String> emails
) {

    public boolean isAdmin(String email) {
        if (emails == null) {
            return false;
        }

        for (String adminEmail : emails) {
            if (adminEmail.trim().equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }
}
