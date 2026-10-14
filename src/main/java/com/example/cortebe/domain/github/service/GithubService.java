package com.example.cortebe.domain.github.service;

import com.example.cortebe.domain.github.client.GithubClient;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateCommitRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateTreeRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.TreeEntry;
import com.example.cortebe.domain.github.client.dto.GithubDto.UpdateRefRequest;
import com.example.cortebe.domain.github.event.ProblemSolvedEvent;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    static final int MAX_ATTEMPTS = 3;
    private static final String FILE_MODE = "100644";
    private static final String BLOB = "blob";

    private final GithubClient githubClient;
    private final GithubAppTokenProvider tokenProvider;

    public void saveSolution(ProblemSolvedEvent event) {
        String authorization = "Bearer " + tokenProvider.issueInstallationToken(event.installationId());
        String owner = event.owner();
        String repo = event.repo();
        String branch = githubClient.getRepository(authorization, owner, repo).defaultBranch();
        List<TreeEntry> files = createFiles(event);
        String message = "[%d] %s 풀이".formatted(event.problemId(), event.title());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String parentSha = githubClient.getBranchRef(authorization, owner, repo, branch).object().sha();
            String baseTreeSha = githubClient.getCommit(authorization, owner, repo, parentSha).tree().sha();
            String treeSha = githubClient.createTree(authorization, owner, repo,
                new CreateTreeRequest(baseTreeSha, files)).sha();
            String commitSha = githubClient.createCommit(authorization, owner, repo,
                new CreateCommitRequest(message, treeSha, List.of(parentSha))).sha();
            try {
                githubClient.updateBranchRef(authorization, owner, repo, branch, new UpdateRefRequest(commitSha, false));
                return;
            } catch (FeignException.Conflict | FeignException.UnprocessableEntity e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("브랜치 갱신 충돌로 재시도합니다 ({}/{}): {}/{}", attempt, MAX_ATTEMPTS, owner, repo);
            }
        }
    }

    private List<TreeEntry> createFiles(ProblemSolvedEvent event) {
        String directory = "%d. %s".formatted(event.problemId(), event.title().replaceAll("[/\\\\\\p{Cntrl}]", "_"));
        String codeFileName = "Main." + event.fileExtension();
        String readme = """
            # [%d] %s

            - 언어: %s
            - 풀이: [%s](%s)

            ## 문제
            %s
            """.formatted(event.problemId(), event.title(), event.language(),
            codeFileName, codeFileName, event.description());
        return List.of(
            new TreeEntry(directory + "/README.md", FILE_MODE, BLOB, readme),
            new TreeEntry(directory + "/" + codeFileName, FILE_MODE, BLOB, event.sourceCode()));
    }
}
