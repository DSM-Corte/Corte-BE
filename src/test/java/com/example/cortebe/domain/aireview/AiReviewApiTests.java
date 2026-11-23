package com.example.cortebe.domain.aireview;

import com.example.cortebe.domain.aireview.client.ClaudeClient;
import com.example.cortebe.domain.aireview.exception.DuplicateAiReviewException;
import com.example.cortebe.domain.aireview.service.AiReviewStore;
import com.example.cortebe.domain.aireview.service.ClaudeReviewer;
import com.example.cortebe.domain.aireview.dto.AiReviewResponse.Comment;
import com.example.cortebe.domain.aireview.dto.AiReviewResponse.Suggestion;
import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"claude.api-key=test-key", "claude.model=test-model"})
class AiReviewApiTests {

    private static final String CLAUDE_JSON = """
        {"summary":"요약","comments":[{"line":1,"body":"코멘트"}],
         "suggestion":{"title":"제안","description":"설명","code":"print(4)"}}""";

    @Autowired MockMvc mvc;
    @Autowired JudgeJobRepository jobs;
    @Autowired ProblemRepository problems;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwtProvider;
    @Autowired AiReviewStore store;
    @MockitoBean ClaudeClient claudeClient;

    @Test
    void 본인_제출에_AI_리뷰를_생성하고_조회한다() throws Exception {
        // given
        User me = saveUser("me");
        JudgeJob job = saveJob(me, true);
        givenClaudeReturns(CLAUDE_JSON);

        // when & then
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/submissions/" + job.getId() + "/ai-review"))
            .andExpect(jsonPath("$.submissionId").value(job.getId()))
            .andExpect(jsonPath("$.model").value("test-model"))
            .andExpect(jsonPath("$.summary").value("요약"))
            .andExpect(jsonPath("$.comments[0].line").value(1))
            .andExpect(jsonPath("$.comments[0].body").value("코멘트"))
            .andExpect(jsonPath("$.suggestion.code").value("print(4)"));

        mvc.perform(get("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary").value("요약"))
            .andExpect(jsonPath("$.suggestion.title").value("제안"));
    }

    @Test
    void 이미_생성한_제출이면_409_I002를_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        JudgeJob job = saveJob(me, true);
        givenClaudeReturns(CLAUDE_JSON);
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isCreated());

        // when & then
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("I002"));
    }

    @Test
    void 유니크_제약이_동시_중복_저장을_막는다() {
        // given
        User me = saveUser("me");
        JudgeJob job = saveJob(me, true);
        ClaudeReviewer.Result result = new ClaudeReviewer.Result("test-model", "요약",
            List.of(new Comment(1, "c")), new Suggestion("t", "d", "code"));
        store.save(me.getId(), job.getId(), result);

        // when & then
        assertThatThrownBy(() -> store.save(me.getId(), job.getId(), result))
            .isInstanceOf(DuplicateAiReviewException.class);
    }

    @Test
    void 남의_제출이면_403_J002를_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        User other = saveUser("other");
        JudgeJob job = saveJob(other, true);

        // when & then
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("J002"));
        mvc.perform(get("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isForbidden());
    }

    @Test
    void 없는_제출이나_실행이면_404_J001을_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        JudgeJob execution = saveJob(me, false);

        // when & then
        mvc.perform(post("/api/submissions/999999/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("J001"));
        mvc.perform(post("/api/submissions/" + execution.getId() + "/ai-review")
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("J001"));
    }

    @Test
    void 리뷰가_없으면_조회가_404_I001을_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        JudgeJob job = saveJob(me, true);

        // when & then
        mvc.perform(get("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("I001"));
    }

    @Test
    void Claude_응답을_해석할_수_없으면_502_I005를_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        JudgeJob job = saveJob(me, true);
        givenClaudeReturns("JSON 아님");

        // when & then
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("I005"));
    }

    @Test
    void 토큰_없이_요청하면_401_A001을_반환한다() throws Exception {
        // when & then
        mvc.perform(post("/api/submissions/1/ai-review"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
        mvc.perform(get("/api/submissions/1/ai-review"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    private void givenClaudeReturns(String text) {
        given(claudeClient.complete(any(), any(), any())).willReturn(text);
    }

    private JudgeJob saveJob(User author, boolean submission) {
        Problem problem = problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build());
        return jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(author).submission(submission)
            .language(Language.PYTHON).code("print(3)").snapshot("{}").tokenHash("hash").totalCount(1).build());
    }

    private User saveUser(String nickname) {
        return users.saveAndFlush(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
