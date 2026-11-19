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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private EntityManager entityManager;

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
            .andExpect(jsonPath("$.hardestProblem").value(nullValue()));
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

    @Test
    void 테마를_고른_적이_없으면_LIGHT와_BLUE를_준다() throws Exception {
        // given
        String token = tokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when & then
        mockMvc.perform(get("/api/users/me/theme").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("LIGHT"))
            .andExpect(jsonPath("$.color").value("BLUE"));
    }

    @Test
    void 테마를_바꾸면_다음_조회에도_유지된다() throws Exception {
        // given
        String token = tokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when
        mockMvc.perform(patch("/api/users/me/theme").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mode": "DARK", "color": "PINK"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("DARK"))
            .andExpect(jsonPath("$.color").value("PINK"));

        // then
        // 테스트 트랜잭션 안의 캐시가 아니라 DB에 저장된 값을 다시 읽는다
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/users/me/theme").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("DARK"))
            .andExpect(jsonPath("$.color").value("PINK"));
    }

    @Test
    void 목록에_없는_강조색이면_400을_준다() throws Exception {
        // given
        String token = tokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when & then
        mockMvc.perform(patch("/api/users/me/theme").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"color": "PURPLE"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 바꿀_값이_없으면_400을_준다() throws Exception {
        // given
        String token = tokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when & then
        mockMvc.perform(patch("/api/users/me/theme").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 토큰_없이_테마를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/theme"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    private String tokenOf(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
