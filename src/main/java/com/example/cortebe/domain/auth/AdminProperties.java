package com.example.cortebe.domain.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("admin")
public record AdminProperties(
    List<String> emails
) {

    public boolean isAdmin(String email) {
        return emails != null && emails.stream()
            .anyMatch(adminEmail -> adminEmail.trim().equalsIgnoreCase(email));
    }
}
