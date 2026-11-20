package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.user.entity.User;
import lombok.Builder;

@Builder
public record GithubLinkResponse(
    Long installationId,
    String owner,
    String repo
) {
    public static GithubLinkResponse from(User user) {
        return GithubLinkResponse.builder()
            .installationId(user.getGithubInstallationId())
            .owner(user.getGithubOwner())
            .repo(user.getGithubRepo())
            .build();
    }
}
