package com.example.cortebe.domain.user.controller;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private JudgeJobRepository judgeJobRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void 내_잔디를_조회한다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        Problem problem = problemRepository.save(Problem.builder()
            .title("두 수의 합")
            .description("두 수를 더한다")
            .category("구현")
            .memoryLimitMb(256)
            .timeLimitMs(1000)
            .build());
        JudgeJob job = judgeJobRepository.save(JudgeJob.builder()
            .problem(problem)
            .author(me)
            .submission(true)
            .language(Language.PYTHON)
            .code("print(1)")
            .snapshot("")
            .tokenHash("hash")
            .totalCount(1)
            .build());
        judgeJobRepository.finish(job.getId(), Verdict.QUEUED, Verdict.ACCEPTED, 1, 10, "", LocalDateTime.now());

        String token = "Bearer " + jwtProvider.createAccessToken(me.getId());
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

        // when & then
        mockMvc.perform(get("/api/users/me/grass").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].date").value(today))
            .andExpect(jsonPath("$[0].count").value(1));
    }

    @Test
    void 내_통계를_조회한다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        Problem problem = problemRepository.save(Problem.builder()
            .title("두 수의 합")
            .description("두 수를 더한다")
            .category("구현")
            .memoryLimitMb(256)
            .timeLimitMs(1000)
            .build());
        JudgeJob job = judgeJobRepository.save(JudgeJob.builder()
            .problem(problem)
            .author(me)
            .submission(true)
            .language(Language.PYTHON)
            .code("print(1)")
            .snapshot("")
            .tokenHash("hash")
            .totalCount(1)
            .build());
        judgeJobRepository.finish(job.getId(), Verdict.QUEUED, Verdict.ACCEPTED, 1, 10, "", LocalDateTime.now());

        String token = "Bearer " + jwtProvider.createAccessToken(me.getId());

        // when & then
        mockMvc.perform(get("/api/users/me/stats").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solvedCount").value(1))
            .andExpect(jsonPath("$.submissionCount").value(1))
            .andExpect(jsonPath("$.acceptedCount").value(1))
            .andExpect(jsonPath("$.accuracy").value(100.0))
            .andExpect(jsonPath("$.verdicts.ACCEPTED").value(1))
            .andExpect(jsonPath("$.verdicts.WRONG_ANSWER").value(0))
            .andExpect(jsonPath("$.languages[0].name").value("PYTHON"))
            .andExpect(jsonPath("$.difficulties.length()").value(31))
            .andExpect(jsonPath("$.tags").isEmpty())
            .andExpect(jsonPath("$.categories[0].name").value("구현"))
            .andExpect(jsonPath("$.hardestProblem").doesNotExist());
    }

    @Test
    void 토큰_없이_통계를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/stats"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 토큰_없이_잔디를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/grass"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }
}
