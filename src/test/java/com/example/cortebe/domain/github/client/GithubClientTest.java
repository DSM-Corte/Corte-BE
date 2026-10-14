package com.example.cortebe.domain.github.client;

import com.example.cortebe.domain.github.client.dto.GithubDto.CreateCommitRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.CreateTreeRequest;
import com.example.cortebe.domain.github.client.dto.GithubDto.TreeEntry;
import com.example.cortebe.domain.github.client.dto.GithubDto.UpdateRefRequest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GithubClientTest {

    private static final String AUTH = "Bearer token";
    private static final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private static final Map<String, StubResponse> stubs = new ConcurrentHashMap<>();
    private static HttpServer server;

    @Autowired
    private GithubClient githubClient;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(key, exchange.getRequestHeaders(), body));
            StubResponse stub = stubs.getOrDefault(key, new StubResponse(200, "{}"));
            byte[] response = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @DynamicPropertySource
    static void githubApiUrl(DynamicPropertyRegistry registry) {
        registry.add("github.api-url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        requests.clear();
        stubs.clear();
    }

    @Test
    void installation_token을_발급받는다() {
        // given
        stubs.put("POST /app/installations/42/access_tokens",
            new StubResponse(201, "{\"token\":\"ghs_abc\",\"expires_at\":\"2026-10-14T10:00:00Z\",\"permissions\":{}}"));

        // when
        String token = githubClient.createInstallationToken("Bearer jwt", 42L).token();

        // then
        assertThat(token).isEqualTo("ghs_abc");
        RecordedRequest request = requests.getFirst();
        assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer jwt");
        assertThat(request.headers().getFirst("Accept")).isEqualTo("application/vnd.github+json");
        assertThat(request.headers().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
    }

    @Test
    void 레포의_기본_브랜치와_ref와_커밋을_조회한다() {
        // given
        stubs.put("GET /repos/owner/repo", new StubResponse(200, "{\"default_branch\":\"main\"}"));
        stubs.put("GET /repos/owner/repo/git/ref/heads/main", new StubResponse(200, "{\"object\":{\"sha\":\"c1\"}}"));
        stubs.put("GET /repos/owner/repo/git/commits/c1", new StubResponse(200, "{\"sha\":\"c1\",\"tree\":{\"sha\":\"t1\"}}"));

        // when
        String branch = githubClient.getRepository(AUTH, "owner", "repo").defaultBranch();
        String commitSha = githubClient.getBranchRef(AUTH, "owner", "repo", branch).object().sha();
        String treeSha = githubClient.getCommit(AUTH, "owner", "repo", commitSha).tree().sha();

        // then
        assertThat(branch).isEqualTo("main");
        assertThat(commitSha).isEqualTo("c1");
        assertThat(treeSha).isEqualTo("t1");
        assertThat(requests.getFirst().headers().getFirst("Authorization")).isEqualTo(AUTH);
    }

    @Test
    void tree와_커밋을_만들고_브랜치를_갱신한다() {
        // given
        stubs.put("POST /repos/owner/repo/git/trees", new StubResponse(201, "{\"sha\":\"t2\"}"));
        stubs.put("POST /repos/owner/repo/git/commits", new StubResponse(201, "{\"sha\":\"c2\"}"));
        CreateTreeRequest treeRequest = new CreateTreeRequest("t1",
            List.of(new TreeEntry("1000. A+B/README.md", "100644", "blob", "# 내용")));

        // when
        String treeSha = githubClient.createTree(AUTH, "owner", "repo", treeRequest).sha();
        String commitSha = githubClient.createCommit(AUTH, "owner", "repo",
            new CreateCommitRequest("[1000] A+B 풀이", treeSha, List.of("c1"))).sha();
        githubClient.updateBranchRef(AUTH, "owner", "repo", "main", new UpdateRefRequest(commitSha, false));

        // then
        assertThat(treeSha).isEqualTo("t2");
        assertThat(commitSha).isEqualTo("c2");
        assertThat(requests).extracting(RecordedRequest::key).containsExactly(
            "POST /repos/owner/repo/git/trees",
            "POST /repos/owner/repo/git/commits",
            "PATCH /repos/owner/repo/git/refs/heads/main");
        assertThat(requests.get(0).body())
            .contains("\"base_tree\":\"t1\"")
            .contains("\"path\":\"1000. A+B/README.md\"")
            .contains("\"content\":\"# 내용\"");
        assertThat(requests.get(1).body()).contains("\"tree\":\"t2\"").contains("\"parents\":[\"c1\"]");
        assertThat(requests.get(2).body()).isEqualTo("{\"sha\":\"c2\",\"force\":false}");
    }

    @Test
    void 브랜치_갱신이_422면_UnprocessableEntity를_던진다() {
        // given
        stubs.put("PATCH /repos/owner/repo/git/refs/heads/main",
            new StubResponse(422, "{\"message\":\"Update is not a fast forward\"}"));

        // when & then
        assertThatThrownBy(() -> githubClient.updateBranchRef(AUTH, "owner", "repo", "main",
            new UpdateRefRequest("c2", false)))
            .isInstanceOf(FeignException.UnprocessableEntity.class);
    }

    private record RecordedRequest(String key, Headers headers, String body) {
    }

    private record StubResponse(int status, String body) {
    }
}
