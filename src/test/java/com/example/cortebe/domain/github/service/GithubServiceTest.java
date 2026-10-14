package com.example.cortebe.domain.github.service;

import com.example.cortebe.domain.github.client.GithubClient;
import com.example.cortebe.domain.github.client.dto.GithubDto.CommitResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateCommitRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateTreeRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.GitObject;
import com.example.cortebe.domain.github.client.dto.GithubDto.RefResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.RepositoryResponse;
import com.example.cortebe.domain.github.client.dto.GithubDto.TreeEntry;
import com.example.cortebe.domain.github.client.dto.GithubDto.UpdateRefRequest;
import com.example.cortebe.domain.github.event.ProblemSolvedEvent;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    private static final String AUTH = "Bearer ghs_abc";

    @Mock
    private GithubClient githubClient;

    @Mock
    private GithubAppTokenProvider tokenProvider;

    @InjectMocks
    private GithubService githubService;

    private final ProblemSolvedEvent event = ProblemSolvedEvent.builder()
        .installationId(42L)
        .owner("owner")
        .repo("repo")
        .problemId(1000L)
        .title("A+B")
        .description("두 정수 A와 B를 입력받은 다음, A+B를 출력하라.")
        .language("Java")
        .fileExtension("java")
        .sourceCode("class Main {}")
        .build();

    @BeforeEach
    void setUp() {
        given(tokenProvider.issueInstallationToken(42L)).willReturn("ghs_abc");
        given(githubClient.getRepository(AUTH, "owner", "repo")).willReturn(new RepositoryResponse("main"));
    }

    @Test
    void README와_코드를_커밋_하나로_저장한다() {
        // given
        givenCommitFlowOnce("c1", "t1", "t2", "c2");

        // when
        githubService.saveSolution(event);

        // then
        ArgumentCaptor<CreateTreeRequest> treeCaptor = ArgumentCaptor.forClass(CreateTreeRequest.class);
        verify(githubClient).createTree(eq(AUTH), eq("owner"), eq("repo"), treeCaptor.capture());
        CreateTreeRequest treeRequest = treeCaptor.getValue();
        assertThat(treeRequest.baseTree()).isEqualTo("t1");
        assertThat(treeRequest.tree()).containsExactly(
            new TreeEntry("1000. A+B/README.md", "100644", "blob", """
                # [1000] A+B

                - 언어: Java
                - 풀이: [Main.java](Main.java)

                ## 문제
                두 정수 A와 B를 입력받은 다음, A+B를 출력하라.
                """),
            new TreeEntry("1000. A+B/Main.java", "100644", "blob", "class Main {}"));
        verify(githubClient).createCommit(AUTH, "owner", "repo",
            new CreateCommitRequest("[1000] A+B 풀이", "t2", List.of("c1")));
        verify(githubClient).updateBranchRef(AUTH, "owner", "repo", "main", new UpdateRefRequest("c2", false));
    }

    @Test
    void 제목의_경로_구분자와_제어문자는_밑줄로_바꾼다() {
        // given
        ProblemSolvedEvent slashTitle = ProblemSolvedEvent.builder()
            .installationId(42L).owner("owner").repo("repo")
            .problemId(7L).title("A/B\\C\nD").description("설명")
            .language("Python").fileExtension("py").sourceCode("print(1)")
            .build();
        givenCommitFlowOnce("c1", "t1", "t2", "c2");

        // when
        githubService.saveSolution(slashTitle);

        // then
        ArgumentCaptor<CreateTreeRequest> treeCaptor = ArgumentCaptor.forClass(CreateTreeRequest.class);
        verify(githubClient).createTree(eq(AUTH), eq("owner"), eq("repo"), treeCaptor.capture());
        assertThat(treeCaptor.getValue().tree()).extracting(TreeEntry::path)
            .containsExactly("7. A_B_C_D/README.md", "7. A_B_C_D/Main.py");
    }

    @Test
    void 브랜치_갱신이_충돌하면_최신_ref로_다시_커밋한다() {
        // given
        given(githubClient.getBranchRef(AUTH, "owner", "repo", "main"))
            .willReturn(new RefResponse(new GitObject("c1")), new RefResponse(new GitObject("c9")));
        given(githubClient.getCommit(AUTH, "owner", "repo", "c1")).willReturn(new CommitResponse("c1", new GitObject("t1")));
        given(githubClient.getCommit(AUTH, "owner", "repo", "c9")).willReturn(new CommitResponse("c9", new GitObject("t9")));
        given(githubClient.createTree(eq(AUTH), eq("owner"), eq("repo"), any())).willReturn(new GitObject("t2"));
        given(githubClient.createCommit(eq(AUTH), eq("owner"), eq("repo"), any()))
            .willReturn(new GitObject("c2"), new GitObject("c10"));
        willThrow(feignException(422)).given(githubClient)
            .updateBranchRef(AUTH, "owner", "repo", "main", new UpdateRefRequest("c2", false));
        willDoNothing().given(githubClient)
            .updateBranchRef(AUTH, "owner", "repo", "main", new UpdateRefRequest("c10", false));

        // when
        githubService.saveSolution(event);

        // then
        verify(githubClient).createCommit(AUTH, "owner", "repo",
            new CreateCommitRequest("[1000] A+B 풀이", "t2", List.of("c9")));
        verify(githubClient).updateBranchRef(AUTH, "owner", "repo", "main", new UpdateRefRequest("c10", false));
    }

    @Test
    void 충돌이_3번_계속되면_예외를_던진다() {
        // given
        givenCommitFlowOnce("c1", "t1", "t2", "c2");
        willThrow(feignException(409)).given(githubClient)
            .updateBranchRef(eq(AUTH), eq("owner"), eq("repo"), eq("main"), any());

        // when & then
        assertThatThrownBy(() -> githubService.saveSolution(event))
            .isInstanceOf(FeignException.Conflict.class);
        verify(githubClient, times(GithubService.MAX_ATTEMPTS))
            .updateBranchRef(eq(AUTH), eq("owner"), eq("repo"), eq("main"), any());
    }

    @Test
    void 빈_레포처럼_ref_조회가_실패하면_재시도하지_않고_예외를_던진다() {
        // given
        given(githubClient.getBranchRef(AUTH, "owner", "repo", "main")).willThrow(feignException(409));

        // when & then
        assertThatThrownBy(() -> githubService.saveSolution(event))
            .isInstanceOf(FeignException.Conflict.class);
        verify(githubClient, times(1)).getBranchRef(AUTH, "owner", "repo", "main");
        verify(githubClient, never()).createTree(anyString(), anyString(), anyString(), any());
    }

    @Test
    void tree_생성이_422면_재시도하지_않고_예외를_던진다() {
        // given
        given(githubClient.getBranchRef(AUTH, "owner", "repo", "main")).willReturn(new RefResponse(new GitObject("c1")));
        given(githubClient.getCommit(AUTH, "owner", "repo", "c1")).willReturn(new CommitResponse("c1", new GitObject("t1")));
        given(githubClient.createTree(eq(AUTH), eq("owner"), eq("repo"), any())).willThrow(feignException(422));

        // when & then
        assertThatThrownBy(() -> githubService.saveSolution(event))
            .isInstanceOf(FeignException.UnprocessableEntity.class);
        verify(githubClient, times(1)).createTree(anyString(), anyString(), anyString(), any());
    }

    private void givenCommitFlowOnce(String parentSha, String baseTreeSha, String treeSha, String commitSha) {
        given(githubClient.getBranchRef(AUTH, "owner", "repo", "main")).willReturn(new RefResponse(new GitObject(parentSha)));
        given(githubClient.getCommit(AUTH, "owner", "repo", parentSha))
            .willReturn(new CommitResponse(parentSha, new GitObject(baseTreeSha)));
        given(githubClient.createTree(eq(AUTH), eq("owner"), eq("repo"), any())).willReturn(new GitObject(treeSha));
        given(githubClient.createCommit(eq(AUTH), eq("owner"), eq("repo"), any())).willReturn(new GitObject(commitSha));
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.PATCH, "https://api.github.com", Map.of(),
            null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("GithubClient#updateBranchRef",
            feign.Response.builder().status(status).reason("error").request(request).headers(Map.of()).build());
    }
}
