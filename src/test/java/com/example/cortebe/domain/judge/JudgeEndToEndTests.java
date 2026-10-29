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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@EnabledIfEnvironmentVariable(named = "JUDGE0_TESTS", matches = "true")
class JudgeEndToEndTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired ProblemRepository problems;
    @Autowired TestCaseService cases;
    @Autowired JudgeJobRepository jobs;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource
    static void judge0(DynamicPropertyRegistry registry) {
        registry.add("judge0.url", () -> System.getenv().getOrDefault("JUDGE0_URL", "http://localhost:2358"));
        registry.add("judge0.poll-interval", () -> "100ms");
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
    void 마지막_줄바꿈이_다르면_오답이고_파이썬_문법오류는_실행오류다() throws Exception {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("", "3\n", false, 0));
        // when & then
        assertThat(result(create("submissions", id, "print(3,end='')")).path("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(result(create("submissions", id, "invalid syntax !!!")).path("verdict").asText()).isEqualTo("RUNTIME_ERROR");
    }

    @Test
    void 미리_작성한_입력을_실행하고_출력과_접근권한을_확인한다() throws Exception {
        // given
        Long id = problem();
        JobResponse created = create("executions", id, "import sys;print(sys.stdin.read(),end='')", "hello\n");
        // when
        JsonNode result = result(created, "executions");
        // then
        assertThat(result.path("verdict").asText()).isEqualTo("FINISHED");
        assertThat(result.path("stdout").asText()).isEqualTo("hello\n");
        assertThat(jobs.findById(created.id()).orElseThrow().getAuthor()).isNull();
        assertThat(get(created.id(), "wrong", "executions").statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void 여섯_언어의_표준입출력을_기존_Judge0로_실행한다(Language language) throws Exception {
        // given
        String code = switch (language) {
            case C -> "#include <stdio.h>\nint main(){int a,b;scanf(\"%d%d\",&a,&b);printf(\"%d\\n\",a+b);}";
            case CPP -> "#include <iostream>\nint main(){int a,b;std::cin>>a>>b;std::cout<<a+b<<'\\n';}";
            case JAVA -> "import java.util.*;class Main{public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}";
            case CSHARP -> "using System;class Program{static void Main(){var a=Console.ReadLine().Split();Console.WriteLine(int.Parse(a[0])+int.Parse(a[1]));}}";
            case JAVASCRIPT -> "const a=require('fs').readFileSync(0,'utf8').trim().split(/\\s+/).map(Number);console.log(a[0]+a[1]);";
            case PYTHON -> "a,b=map(int,input().split());print(a+b)";
        };
        // when
        JobResponse created = create("executions", problem(), code, "1 2\n", language);
        JsonNode result = result(created, "executions");
        // then
        assertThat(result.path("verdict").asText()).as(result.toString()).isEqualTo("FINISHED");
        assertThat(result.path("stdout").asText()).isEqualTo("3\n");
    }

    @Test
    void 컴파일언어의_오류는_컴파일진단과_함께_반환한다() throws Exception {
        // given & when
        JobResponse created = create("executions", problem(), "int main( invalid syntax", "", Language.C);
        JsonNode result = result(created, "executions");
        // then
        assertThat(result.path("verdict").asText()).isEqualTo("COMPILE_ERROR");
        assertThat(result.path("message").asText()).isNotBlank();
    }

    private Long problem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(3000).memoryLimitMb(256).build()).getId();
    }

    private JobResponse create(String type, Long id, String code) throws Exception {
        return create(type, id, code, null);
    }

    private JobResponse create(String type, Long id, String code, String stdin) throws Exception {
        return create(type, id, code, stdin, Language.PYTHON);
    }

    private JobResponse create(String type, Long id, String code, String stdin, Language language) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/" + type))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(new CreateJobRequest(id, language, code, stdin)))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return mapper.readValue(response.body(), JobResponse.class);
    }

    private HttpResponse<String> get(Long id, String token) throws Exception {
        return get(id, token, "submissions");
    }

    private HttpResponse<String> get(Long id, String token, String type) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/" + type + "/" + id))
            .header("X-Judge-Token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode result(JobResponse response) throws Exception { return result(response, "submissions"); }

    private JsonNode result(JobResponse response, String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            HttpResponse<String> http = get(response.id(), response.accessToken(), type);
            assertThat(http.statusCode()).as(http.body()).isEqualTo(200);
            JsonNode result = mapper.readTree(http.body());
            String verdict = result.path("verdict").asText();
            if (!verdict.equals("QUEUED") && !verdict.equals("RUNNING")) return result;
            Thread.sleep(50);
        }
        throw new AssertionError("채점 대기 시간이 초과되었습니다.");
    }

}
