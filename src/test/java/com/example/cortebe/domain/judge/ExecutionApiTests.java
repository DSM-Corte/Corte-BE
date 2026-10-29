package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;
import com.example.cortebe.domain.judge.service.JudgeProvider;
import com.example.cortebe.domain.judge.service.TestCaseService;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"judge.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:executionapi;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExecutionApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ProblemRepository problems;
    @Autowired JudgeJobRepository jobs;
    @Autowired TestCaseService cases;
    @MockitoBean JudgeProvider provider;

    @BeforeEach
    void setUp() {
        given(provider.judge(any())).willAnswer(invocation -> {
            var request = (com.example.cortebe.domain.judge.dto.JudgeRequest) invocation.getArgument(0);
            return JudgeResult.builder().status(JudgeStatus.ACCEPTED).timeMs(12).memoryKb(100L)
                .cases(request.cases().stream().map(value ->
                    new JudgeResult.CaseResult(JudgeStatus.ACCEPTED, 12, 100L, "3\n", "err", "")).toList()).build();
        });
    }

    @Test
    void 비회원_실행은_조회주소와_토큰을_반환하고_출력을_조회한다() throws Exception {
        // given
        Long id = problem();
        // when
        var response = mvc.perform(post("/api/executions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":" + id + ",\"language\":\"PYTHON\",\"code\":\"print(3)\",\"stdin\":\"1 2\\n\"}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.accessToken").isString()).andReturn();
        JsonNode created = mapper.readTree(response.getResponse().getContentAsString());
        String location = response.getResponse().getHeader("Location");
        // then
        assertThat(location).isEqualTo("/api/executions/" + created.path("id").asLong());
        JsonNode result = await(location, created.path("accessToken").asString());
        assertThat(result.path("verdict").asString()).isEqualTo("FINISHED");
        assertThat(result.path("stdout").asString()).isEqualTo("3\n");
        assertThat(result.path("stderr").asString()).isEqualTo("err");
        assertThat(jobs.findById(created.path("id").asLong()).orElseThrow().getAuthor()).isNull();
        mvc.perform(get(location)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("J002"));
        mvc.perform(get(location).header("X-Judge-Token", "wrong")).andExpect(status().isForbidden());
    }

    @Test
    void 비회원_제출도_기존_공급자에_연결하고_비공개_출력을_노출하지_않는다() throws Exception {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("1 2\n", "3\n", false, 0));
        // when
        var response = mvc.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":" + id + ",\"language\":\"PYTHON\",\"code\":\"print(3)\"}"))
            .andExpect(status().isCreated()).andReturn();
        JsonNode created = mapper.readTree(response.getResponse().getContentAsString());
        // then
        JsonNode result = await(response.getResponse().getHeader("Location"), created.path("accessToken").asString());
        assertThat(result.path("verdict").asString()).isEqualTo("ACCEPTED");
        assertThat(result.path("passedCount").asInt()).isEqualTo(1);
        assertThat(result.path("stdout").isNull()).isTrue();
        assertThat(result.path("stderr").isNull()).isTrue();
    }

    @Test
    void 잘못된_언어와_코드요청은_400이고_실행조회_종류가_다르면_404다() throws Exception {
        // when & then
        mvc.perform(post("/api/executions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"problemId\":1,\"language\":\"UNKNOWN\",\"code\":\"code\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/executions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"problemId\":1,\"language\":\"PYTHON\",\"code\":\" \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        mvc.perform(get("/api/executions/999999").header("X-Judge-Token", "wrong"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("J001"));
    }

    private Long problem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
    }

    private JsonNode await(String location, String token) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        do {
            var response = mvc.perform(get(location).header("X-Judge-Token", token)).andExpect(status().isOk()).andReturn();
            JsonNode result = mapper.readTree(response.getResponse().getContentAsString());
            if (!List.of("QUEUED", "RUNNING").contains(result.path("verdict").asString())) return result;
            Thread.sleep(10);
        } while (System.nanoTime() < until);
        throw new AssertionError("작업이 완료되지 않았습니다.");
    }
}
