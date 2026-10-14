package com.example.cortebe.domain.github.client;

import com.example.cortebe.domain.github.client.dto.GithubDto.CommitResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateCommitRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateTreeRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.GitObject;
import com.example.cortebe.domain.github.client.dto.GithubDto.InstallationTokenResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.RefResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.RepositoryResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.UpdateRefRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "github", url = "${github.api-url:https://api.github.com}")
public interface GithubClient {

    String ACCEPT = "Accept=application/vnd.github+json";
    String API_VERSION = "X-GitHub-Api-Version=2022-11-28";

    @PostMapping(value = "/app/installations/{installationId}/access_tokens", headers = {ACCEPT, API_VERSION})
    InstallationTokenResponse createInstallationToken(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("installationId") long installationId);

    @GetMapping(value = "/repos/{owner}/{repo}", headers = {ACCEPT, API_VERSION})
    RepositoryResponse getRepository(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo);

    @GetMapping(value = "/repos/{owner}/{repo}/git/ref/heads/{branch}", headers = {ACCEPT, API_VERSION})
    RefResponse getBranchRef(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo,
        @PathVariable("branch") String branch);

    @GetMapping(value = "/repos/{owner}/{repo}/git/commits/{sha}", headers = {ACCEPT, API_VERSION})
    CommitResponse getCommit(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo,
        @PathVariable("sha") String sha);

    @PostMapping(value = "/repos/{owner}/{repo}/git/trees", headers = {ACCEPT, API_VERSION})
    GitObject createTree(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo,
        @RequestBody CreateTreeRequest request);

    @PostMapping(value = "/repos/{owner}/{repo}/git/commits", headers = {ACCEPT, API_VERSION})
    GitObject createCommit(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo,
        @RequestBody CreateCommitRequest request);

    @PatchMapping(value = "/repos/{owner}/{repo}/git/refs/heads/{branch}", headers = {ACCEPT, API_VERSION})
    void updateBranchRef(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo,
        @PathVariable("branch") String branch,
        @RequestBody UpdateRefRequest request);
}
