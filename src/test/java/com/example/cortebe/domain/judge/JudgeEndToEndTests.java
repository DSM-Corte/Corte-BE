package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.dto.UpdateTestCaseRequest;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.judge.service.TestCaseService;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "judge.enabled=true", "spring.datasource.url=jdbc:h2:mem:judgee2e;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "JUDGE_DOCKER_TESTS", matches = "true")
class JudgeEndToEndTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired ProblemRepository problems;
    @Autowired TestCaseService cases;
    @Autowired JudgeJobRepository jobs;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource
    static void docker(DynamicPropertyRegistry registry) {
        registry.add("judge.docker", () -> System.getenv().getOrDefault("JUDGE_DOCKER", "docker"));
    }

    @Test
    void 비회원이_실제_제출하면_비공개_케이스까지_채점하지만_회원기록은_생기지_않는다() throws Exception {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("1 2\n", "3\n", true, 0));
        cases.create(id, new CreateTestCaseRequest("4 5\n", "9\n", false, 1));
        // when
        JobResponse created = create("submissions", id, "a,b=map(int,input().split());print(a+b)");
        JsonNode result = result(created);
        // then
        assertThat(result.path("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(result.path("passedCount").asInt()).isEqualTo(2);
        assertThat(result.has("testResults")).isFalse();
        assertThat(result.toString()).doesNotContain("4 5", "expectedOutput");
        assertThat(jobs.findById(created.id()).orElseThrow().getAuthor()).isNull();
        assertThat(get(created.id(), "wrong").statusCode()).isEqualTo(403);
    }

    @Test
    void 마지막_줄바꿈이_다르면_오답이고_컴파일오류도_구분한다() throws Exception {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("", "3\n", false, 0));
        // when & then
        assertThat(result(create("submissions", id, "print(3,end='')")).path("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(result(create("submissions", id, "invalid syntax !!!")).path("verdict").asText()).isEqualTo("COMPILE_ERROR");
    }

    @Test
    void 웹소켓으로_실행중_입력과_EOF를_전달하고_출력을_받는다() throws Exception {
        // given
        Long id = problem();
        JobResponse created = create("executions", id, "import sys\nprint('ready',flush=True)\na=sys.stdin.read()\nprint(a,end='')");
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        WebSocket socket = connect(created, events);
        // when
        StringBuilder output = new StringBuilder();
        waitFor(events, "ready", output);
        socket.sendText(mapper.writeValueAsString(Map.of("type", "stdin", "data", "한글\n")), true).join();
        socket.sendText("{\"type\":\"eof\"}", true).join();
        JsonNode exit = waitFor(events, "exit", output);
        // then
        assertThat(exit.path("data").asText()).isEqualTo("FINISHED");
        assertThat(output.toString()).isEqualTo("ready\n한글\n");
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "종료").join();
    }

    @Test
    void 웹소켓_취소로_대기중인_프로그램을_종료한다() throws Exception {
        // given
        JobResponse created = create("executions", problem(), "input()");
        java.util.concurrent.CompletableFuture<Integer> closed = new java.util.concurrent.CompletableFuture<>();
        WebSocket forbidden = client.newWebSocketBuilder().buildAsync(
            URI.create("ws://localhost:" + port + created.streamPath()), new WebSocket.Listener() {
                public void onOpen(WebSocket socket) { socket.request(Long.MAX_VALUE); }
                public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                    closed.complete(code); return null;
                }
            }).join();
        forbidden.sendText("{\"type\":\"auth\",\"token\":\"wrong\"}", true).join();
        assertThat(closed.get(10, TimeUnit.SECONDS)).isEqualTo(1008);
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        WebSocket socket = connect(created, events);
        waitFor(events, "ready", new StringBuilder());
        // when
        socket.sendText("{\"type\":\"cancel\"}", true).join();
        // then
        assertThat(waitFor(events, "exit", new StringBuilder()).path("data").asText()).isEqualTo("CANCELLED");
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "종료").join();
    }

    private Long problem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(3000).memoryLimitMb(256).build()).getId();
    }

    private JobResponse create(String type, Long id, String code) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/" + type))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(new CreateJobRequest(id, Language.PYTHON, code)))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return mapper.readValue(response.body(), JobResponse.class);
    }

    private HttpResponse<String> get(Long id, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/submissions/" + id))
            .header("X-Judge-Token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode result(JobResponse response) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            HttpResponse<String> http = get(response.id(), response.accessToken());
            assertThat(http.statusCode()).as(http.body()).isEqualTo(200);
            JsonNode result = mapper.readTree(http.body());
            String verdict = result.path("verdict").asText();
            if (!verdict.equals("QUEUED") && !verdict.equals("RUNNING")) return result;
            Thread.sleep(50);
        }
        throw new AssertionError("채점 대기 시간이 초과되었습니다.");
    }

    private WebSocket connect(JobResponse job, BlockingQueue<JsonNode> events) {
        WebSocket socket = client.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + job.streamPath()),
            new WebSocket.Listener() {
                private final StringBuilder pending = new StringBuilder();
                public void onOpen(WebSocket webSocket) { webSocket.request(Long.MAX_VALUE); }
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    pending.append(data);
                    if (last) { events.add(mapper.readTree(pending.toString())); pending.setLength(0); }
                    return null;
                }
            }).join();
        socket.sendText(mapper.writeValueAsString(Map.of("type", "auth", "token", job.accessToken())), true).join();
        return socket;
    }

    private JsonNode waitFor(BlockingQueue<JsonNode> events, String type, StringBuilder output) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            JsonNode event = events.poll(1, TimeUnit.SECONDS);
            if (event == null) continue;
            if (event.path("type").asText().equals("stdout")) {
                output.append(new String(Base64.getDecoder().decode(event.path("data").asText()), StandardCharsets.UTF_8));
            }
            if (event.path("type").asText().equals(type)) return event;
        }
        throw new AssertionError("실행 이벤트를 받지 못했습니다: " + type);
    }
}
