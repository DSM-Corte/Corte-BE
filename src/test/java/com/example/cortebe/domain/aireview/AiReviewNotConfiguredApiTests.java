package com.example.cortebe.domain.aireview;

import com.example.cortebe.domain.aireview.client.GeminiClient;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"gemini.api-key=", "gemini.model=test-model"})
class AiReviewNotConfiguredApiTests {

    @Autowired MockMvc mvc;
    @Autowired JudgeJobRepository jobs;
    @Autowired ProblemRepository problems;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean GeminiClient geminiClient;

    @Test
    void 키가_없으면_외부_호출_없이_503_I003을_반환한다() throws Exception {
        // given
        User me = users.saveAndFlush(User.builder()
            .email("me@corte.com").password("password").nickname("me").build());
        Problem problem = problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build());
        JudgeJob job = jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(me).submission(true)
            .language(Language.PYTHON).code("print(3)").snapshot("{}").tokenHash("hash").totalCount(1).build());

        // when & then
        mvc.perform(post("/api/submissions/" + job.getId() + "/ai-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(me.getId())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("I003"));
        then(geminiClient).should(never()).generateContent(any(), any(), any());
    }
}
