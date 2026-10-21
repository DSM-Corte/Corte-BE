package com.example.cortebe.domain.github.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class GithubDto {

    private GithubDto() {
    }

    public record InstallationTokenResponse(
        String token,
        @JsonProperty("expires_at") String expiresAt
    ) {
    }

    public record RepositoryResponse(
        @JsonProperty("default_branch") String defaultBranch
    ) {
    }

    public record GitObject(
        String sha
    ) {
    }

    public record RefResponse(
        GitObject object
    ) {
    }

    public record CommitResponse(
        String sha,
        GitObject tree
    ) {
    }

    public record TreeEntry(
        String path,
        String mode,
        String type,
        String content
    ) {
    }

    public record CreateTreeRequest(
        @JsonProperty("base_tree") String baseTree,
        List<TreeEntry> tree
    ) {
    }

    public record CreateCommitRequest(
        String message,
        String tree,
        List<String> parents
    ) {
    }

    public record UpdateRefRequest(
        String sha,
        boolean force
    ) {
    }
}
