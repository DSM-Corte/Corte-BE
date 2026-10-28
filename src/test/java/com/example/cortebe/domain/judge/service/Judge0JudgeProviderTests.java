package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.JudgeLanguage;
import com.example.cortebe.domain.judge.JudgeStatus;
import com.example.cortebe.domain.judge.dto.JudgeCase;
import com.example.cortebe.domain.judge.dto.JudgeRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;
import com.example.cortebe.domain.judge.dto.JudgeResult.CaseResult;
import com.example.cortebe.domain.judge.exception.JudgeFetchFailedException;
import com.example.cortebe.domain.judge.exception.JudgeSubmitFailedException;
import com.example.cortebe.domain.judge.exception.JudgeTimeoutException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Judge0JudgeProviderTests {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<JsonNode> submitBodies = new CopyOnWriteArrayList<>();
    private static final List<String> submitQueries = new CopyOnWriteArrayList<>();
    private static final List<String> fetchQueries = new CopyOnWriteArrayList<>();
    private static final List<List<String>> fetchedTokens = new CopyOnWriteArrayList<>();
    private static final Map<String, FakeCase> fakeCases = new ConcurrentHashMap<>();
    private static final List<FakeCase> nextCases = new CopyOnWriteArrayList<>();
    private static volatile int submitStatus;
    private static volatile String submitResponseOverride;
    private static volatile int fetchStatus;
    private static volatile String fetchResponseOverride;
    private static HttpServer server;

    @Autowired
    private JudgeProvider judgeProvider;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submissions/batch", exchange -> {
            String response;
            int status;
            if (exchange.getRequestMethod().equals("POST")) {
                submitQueries.add(exchange.getRequestURI().getRawQuery());
                JsonNode body = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
                submitBodies.add(body);
                status = submitStatus;
                response = submitResponseOverride != null ? submitResponseOverride : issueTokens(body.get("submissions").size());
            } else {
                fetchQueries.add(exchange.getRequestURI().getRawQuery());
                List<String> tokens = Arrays.asList(queryParam(exchange.getRequestURI().getRawQuery(), "tokens").split(","));
                fetchedTokens.add(tokens);
                status = fetchStatus;
                response = fetchResponseOverride != null ? fetchResponseOverride : submissions(tokens);
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @DynamicPropertySource
    static void judge0Properties(DynamicPropertyRegistry registry) {
        registry.add("judge0.url", () -> "http://localhost:" + server.getAddress().getPort());
        registry.add("judge0.poll-interval", () -> "10ms");
        registry.add("judge0.idle-timeout", () -> "1s");
    }

    @BeforeEach
    void reset() {
        submitBodies.clear();
        submitQueries.clear();
        fetchQueries.clear();
        fetchedTokens.clear();
        fakeCases.clear();
        nextCases.clear();
        submitStatus = 201;
        submitResponseOverride = null;
        fetchStatus = 200;
        fetchResponseOverride = null;
    }

    @Test
    void 모든_테스트케이스가_맞으면_ACCEPTED와_시간_메모리_최댓값과_케이스별_결과를_돌려준다() {
        // given
        givenCase("0.012", 3000L, 3);
        givenCase("0.020", 2000L, 3);

        // when
        JudgeResult result = judgeProvider.judge(request(JudgeLanguage.PYTHON, 2));

        // then
        assertThat(result.status()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(result.timeMs()).isEqualTo(20);
        assertThat(result.memoryKb()).isEqualTo(3000L);
        assertThat(result.cases()).containsExactly(
            new CaseResult(JudgeStatus.ACCEPTED, 12, 3000L),
            new CaseResult(JudgeStatus.ACCEPTED, 20, 2000L));
    }

    @Test
    void 하나라도_틀리면_요청_순서에서_처음_실패한_판정이_전체_판정이다() {
        // given
        givenCase("0.010", 1000L, 3);
        givenCase("0.030", 1500L, 4);
        givenCase(null, null, 6);

        // when
        JudgeResult result = judgeProvider.judge(request(JudgeLanguage.CPP17, 3));

        // then
        assertThat(result.status()).isEqualTo(JudgeStatus.WRONG_ANSWER);
        assertThat(result.timeMs()).isEqualTo(30);
        assertThat(result.memoryKb()).isEqualTo(1500L);
        assertThat(result.cases()).extracting(CaseResult::status).containsExactly(
            JudgeStatus.ACCEPTED, JudgeStatus.WRONG_ANSWER, JudgeStatus.COMPILE_ERROR);
        assertThat(result.cases().get(2)).isEqualTo(new CaseResult(JudgeStatus.COMPILE_ERROR, null, null));
    }

    @Test
    void 채점_중인_테스트케이스는_끝날_때까지_다시_조회한다() {
        // given
        givenCase("0.010", 1000L, 1, 2, 3);
        givenCase("0.010", 1000L, 3);

        // when
        JudgeResult result = judgeProvider.judge(request(JudgeLanguage.JAVA, 2));

        // then
        assertThat(result.status()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(fetchedTokens).hasSize(3);
        assertThat(fetchedTokens.get(0)).containsExactly("t0", "t1");
        assertThat(fetchedTokens.get(1)).containsExactly("t0");
        assertThat(fetchedTokens.get(2)).containsExactly("t0");
    }

    @Test
    void 결과_조회는_필요한_필드를_한_번에_base64로_요청한다() {
        // given
        givenCase("0.001", 100L, 3);

        // when
        judgeProvider.judge(request(JudgeLanguage.PYTHON, 1));

        // then
        String query = fetchQueries.getFirst();
        assertThat(queryParams(query, "fields")).containsExactly("token,status,time,memory");
        assertThat(queryParams(query, "base64_encoded")).containsExactly("true");
    }

    @Test
    void 테스트케이스가_20개를_넘으면_20개씩_나눠_제출하고_조회한다() {
        // given
        IntStream.range(0, 21).forEach(i -> givenCase("0.001", 100L, 3));

        // when
        JudgeResult result = judgeProvider.judge(request(JudgeLanguage.C99, 21));

        // then
        assertThat(result.cases()).hasSize(21);
        assertThat(submitBodies).extracting(body -> body.get("submissions").size()).containsExactly(20, 1);
        assertThat(fetchedTokens).extracting(List::size).containsExactly(20, 1);
    }

    @Test
    void 코드와_입출력은_base64로_언어_ID와_제한은_Judge0_단위로_보낸다() {
        // given
        givenCase("0.001", 100L, 3);
        givenCase("0.001", 100L, 3);
        JudgeRequest request = JudgeRequest.builder()
            .language(JudgeLanguage.CPP17)
            .code("int main() {}")
            .cases(List.of(new JudgeCase("1 2\n", "3\n"), new JudgeCase("한글", "출력")))
            .timeLimitMs(1500)
            .memoryLimitMb(256)
            .build();

        // when
        judgeProvider.judge(request);

        // then
        assertThat(submitQueries.getFirst()).contains("base64_encoded=true");
        JsonNode submission = submitBodies.getFirst().get("submissions").get(1);
        assertThat(decode(submission.get("source_code"))).isEqualTo("int main() {}");
        assertThat(decode(submission.get("stdin"))).isEqualTo("한글");
        assertThat(decode(submission.get("expected_output"))).isEqualTo("출력");
        assertThat(submission.get("language_id").asInt()).isEqualTo(54);
        assertThat(submission.get("compiler_options").asString()).isEqualTo("-std=c++17");
        assertThat(submission.get("cpu_time_limit").asDouble()).isEqualTo(1.5);
        assertThat(submission.get("memory_limit").asLong()).isEqualTo(262144L);
    }

    @Test
    void 컴파일_옵션이_없는_언어는_compiler_options를_보내지_않는다() {
        // given
        givenCase("0.001", 100L, 3);

        // when
        judgeProvider.judge(request(JudgeLanguage.JAVA, 1));

        // then
        JsonNode submission = submitBodies.getFirst().get("submissions").get(0);
        assertThat(submission.get("language_id").asInt()).isEqualTo(62);
        assertThat(submission.has("compiler_options")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "3, ACCEPTED", "4, WRONG_ANSWER", "5, TIME_LIMIT_EXCEEDED", "6, COMPILE_ERROR",
        "7, RUNTIME_ERROR", "8, RUNTIME_ERROR", "9, RUNTIME_ERROR", "10, RUNTIME_ERROR",
        "11, RUNTIME_ERROR", "12, RUNTIME_ERROR", "13, INTERNAL_ERROR", "14, INTERNAL_ERROR",
        "99, INTERNAL_ERROR"
    })
    void Judge0_상태_ID를_판정으로_바꾸고_모르는_ID는_INTERNAL_ERROR로_본다(int statusId, JudgeStatus expected) {
        // given
        givenCase("0.001", 100L, statusId);

        // when
        JudgeResult result = judgeProvider.judge(request(JudgeLanguage.PYTHON, 1));

        // then
        assertThat(result.status()).isEqualTo(expected);
    }

    @Test
    void 제출이_HTTP_오류면_JudgeSubmitFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        submitStatus = 500;

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 1)))
            .isInstanceOf(JudgeSubmitFailedException.class);
    }

    @Test
    void 제출_응답에_토큰이_없으면_JudgeSubmitFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        submitResponseOverride = "[{\"language_id\":[\"language with id 999 doesn't exist\"]}]";

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 1)))
            .isInstanceOf(JudgeSubmitFailedException.class);
    }

    @Test
    void 제출_응답의_토큰_개수가_제출_개수와_다르면_JudgeSubmitFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        givenCase("0.001", 100L, 3);
        submitResponseOverride = "[{\"token\":\"t0\"}]";

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 2)))
            .isInstanceOf(JudgeSubmitFailedException.class);
    }

    @Test
    void 결과_조회_응답에_submissions가_없으면_JudgeFetchFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        fetchResponseOverride = "{}";

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 1)))
            .isInstanceOf(JudgeFetchFailedException.class);
    }

    @Test
    void 결과_조회_응답에_status나_token이_없으면_JudgeFetchFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        fetchResponseOverride = "{\"submissions\":[{\"memory\":100}]}";

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 1)))
            .isInstanceOf(JudgeFetchFailedException.class);
    }

    @Test
    void 결과_조회가_HTTP_오류면_JudgeFetchFailedException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        fetchStatus = 500;

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 1)))
            .isInstanceOf(JudgeFetchFailedException.class);
    }

    @Test
    void 끝난_케이스가_idle_timeout_동안_늘지_않으면_JudgeTimeoutException을_던진다() {
        // given
        givenCase("0.001", 100L, 3);
        givenCase(null, null, 1);

        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 2)))
            .isInstanceOf(JudgeTimeoutException.class);
    }

    @Test
    void 테스트케이스가_비어_있으면_IllegalArgumentException을_던진다() {
        // when & then
        assertThatThrownBy(() -> judgeProvider.judge(request(JudgeLanguage.PYTHON, 0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(submitBodies).isEmpty();
    }

    // 상태 ID를 차례로 돌려주고, 마지막 상태는 계속 유지한다
    private void givenCase(String time, Long memory, int... statusIds) {
        nextCases.add(new FakeCase(time, memory, new ArrayList<>(Arrays.stream(statusIds).boxed().toList())));
    }

    private JudgeRequest request(JudgeLanguage language, int caseCount) {
        return JudgeRequest.builder()
            .language(language)
            .code("code")
            .cases(IntStream.range(0, caseCount).mapToObj(i -> new JudgeCase("in" + i, "out" + i)).toList())
            .timeLimitMs(1000)
            .memoryLimitMb(128)
            .build();
    }

    private static String issueTokens(int count) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String token = "t" + fakeCases.size();
            fakeCases.put(token, nextCases.get(fakeCases.size()));
            tokens.add("{\"token\":\"" + token + "\"}");
        }
        return "[" + String.join(",", tokens) + "]";
    }

    private static String submissions(List<String> tokens) {
        List<String> submissions = tokens.stream().map(token -> {
            FakeCase fakeCase = fakeCases.get(token);
            int statusId = fakeCase.statusIds().size() > 1 ? fakeCase.statusIds().removeFirst() : fakeCase.statusIds().getFirst();
            return "{\"token\":\"" + token + "\",\"status\":{\"id\":" + statusId + ",\"description\":\"\"},"
                + "\"time\":" + (fakeCase.time() == null ? "null" : "\"" + fakeCase.time() + "\"") + ","
                + "\"memory\":" + fakeCase.memory() + "}";
        }).toList();
        return "{\"submissions\":[" + String.join(",", submissions) + "]}";
    }

    private static String queryParam(String rawQuery, String name) {
        return queryParams(rawQuery, name).getFirst();
    }

    private static List<String> queryParams(String rawQuery, String name) {
        return Arrays.stream(rawQuery.split("&"))
            .map(pair -> pair.split("=", 2))
            .filter(pair -> pair[0].equals(name))
            .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
            .toList();
    }

    private static String decode(JsonNode node) {
        return new String(Base64.getDecoder().decode(node.asString()), StandardCharsets.UTF_8);
    }

    private record FakeCase(String time, Long memory, List<Integer> statusIds) {
    }
}
