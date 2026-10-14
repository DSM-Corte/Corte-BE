package com.example.cortebe.domain.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("github.app")
public record GithubAppProperties(
    String clientId,
    String privateKey
) {
}
