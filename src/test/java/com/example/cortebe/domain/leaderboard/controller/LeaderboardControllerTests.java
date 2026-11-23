package com.example.cortebe.domain.leaderboard.controller;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaderboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private JudgeJobRepository jobRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User solver;

    @BeforeEach
    void setUp() {
        solver = saveUser("solver");
        Problem problem = problemRepository.save(Problem.builder().title("두 수의 합").description("설명")
            .category("구현").timeLimitMs(1000).memoryLimitMb(256).difficulty(Difficulty.SILVER_5).build());
        saveJob(problem, solver, Verdict.ACCEPTED);
        saveJob(problem, solver, Verdict.WRONG_ANSWER);
    }

    @Test
    void 토큰_없이도_리더보드를_조회한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[0].userId").value(solver.getId()))
            .andExpect(jsonPath("$.content[0].nickname").value("solver"))
            .andExpect(jsonPath("$.content[0].tier").value("BRONZE_5"))
            .andExpect(jsonPath("$.content[0].xp").value(7))
            .andExpect(jsonPath("$.content[0].solvedCount").value(1))
            .andExpect(jsonPath("$.content[0].submissionCount").value(2))
            .andExpect(jsonPath("$.content[0].accuracy").value(50.0))
            .andExpect(jsonPath("$.content[0].streak").value(1))
            .andExpect(jsonPath("$.content[0].me").value(false))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void 로그인하면_내_항목에_me를_표시한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard").header(HttpHeaders.AUTHORIZATION, bearer(solver)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].me").value(true));
    }

    @Test
    void 검색_필터_정렬_파라미터를_받는다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard")
                .param("keyword", "SOL").param("minTier", "BRONZE_5").param("maxTier", "MASTER")
                .param("minSolved", "1").param("maxSolved", "1")
                .param("sort", "XP").param("direction", "ASC").param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void 없는_정렬_이름이면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard").param("sort", "NAME"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 없는_티어_이름이면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard").param("minTier", "RUBY_1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 정수가_아닌_정답_수면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard").param("minSolved", "많이"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/leaderboard").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    private void saveJob(Problem problem, User author, Verdict verdict) {
        JudgeJob job = jobRepository.save(JudgeJob.builder().problem(problem).author(author).submission(true)
            .language(Language.PYTHON).code("print(1)").snapshot("").tokenHash("hash").totalCount(1).build());
        jobRepository.finish(job.getId(), Verdict.QUEUED, verdict, 1, 10, "", LocalDateTime.now());
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
