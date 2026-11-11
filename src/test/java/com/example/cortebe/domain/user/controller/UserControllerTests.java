package com.example.cortebe.domain.user.controller;

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
    void 내_정식_제출을_카테고리와_난이도별로_집계한다() throws Exception {
        // given
        User me = userRepository.save(new User("stats@corte.com", "password", "me"));
        User other = userRepository.save(new User("other@corte.com", "password", "other"));
        Difficulty silver = Difficulty.SILVER_3;
        Difficulty gold = Difficulty.GOLD_5;
        Difficulty unrated = Difficulty.UNRATED;

        Problem solved = saveProblem("Graph", silver);
        saveJob(solved, me, true, Verdict.WRONG_ANSWER);
        saveJob(solved, me, true, Verdict.ACCEPTED);
        saveJob(solved, me, true, Verdict.ACCEPTED);

        for (Verdict verdict : new Verdict[]{Verdict.WRONG_ANSWER, Verdict.COMPILE_ERROR,
            Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT}) {
            saveJob(saveProblem("Graph", silver), me, true, verdict);
        }

        Problem noRecord = saveProblem("Graph", gold);
        saveJob(noRecord, other, true, Verdict.ACCEPTED);
        saveJob(noRecord, me, false, Verdict.ACCEPTED);
        for (Verdict verdict : new Verdict[]{Verdict.QUEUED, Verdict.RUNNING,
            Verdict.SYSTEM_ERROR, Verdict.CANCELLED, Verdict.FINISHED}) {
            saveJob(noRecord, me, true, verdict);
        }

        saveProblem("Graph", null);
        Problem recategorized = saveProblem("Graph", gold);
        saveJob(recategorized, me, true, Verdict.ACCEPTED);
        recategorized = problemRepository.findById(recategorized.getId()).orElseThrow();
        recategorized.update(null, null, "Implementation", null, null, null);
        recategorized.updateDifficulty(unrated);
        String token = "Bearer " + jwtProvider.createAccessToken(me.getId());

        // when & then
        mockMvc.perform(get("/api/users/me/problem-stats").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].category").value("Graph"))
            .andExpect(jsonPath("$[0].difficulty").value("SILVER_3"))
            .andExpect(jsonPath("$[0].attemptedCount").value(7))
            .andExpect(jsonPath("$[0].solvedCount").value(1))
            .andExpect(jsonPath("$[1].category").value("Graph"))
            .andExpect(jsonPath("$[1].difficulty").value("GOLD_5"))
            .andExpect(jsonPath("$[1].attemptedCount").value(0))
            .andExpect(jsonPath("$[1].solvedCount").value(0))
            .andExpect(jsonPath("$[2].category").value("Graph"))
            .andExpect(jsonPath("$[2].difficulty").value("UNRATED"))
            .andExpect(jsonPath("$[2].attemptedCount").value(0))
            .andExpect(jsonPath("$[2].solvedCount").value(0))
            .andExpect(jsonPath("$[3].category").value("Implementation"))
            .andExpect(jsonPath("$[3].difficulty").value("UNRATED"))
            .andExpect(jsonPath("$[3].attemptedCount").value(1))
            .andExpect(jsonPath("$[3].solvedCount").value(1));
    }

    @Test
    void 등록된_문제가_없으면_빈_통계를_준다() throws Exception {
        // given
        User me = userRepository.save(new User("empty@corte.com", "password", "me"));
        String token = "Bearer " + jwtProvider.createAccessToken(me.getId());

        // when & then
        mockMvc.perform(get("/api/users/me/problem-stats").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 토큰_없이_풀이_통계를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/problem-stats"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    private Problem saveProblem(String category, Difficulty difficulty) {
        return problemRepository.save(Problem.builder()
            .title("통계 문제")
            .description("설명")
            .category(category)
            .difficulty(difficulty)
            .memoryLimitMb(256)
            .timeLimitMs(1000)
            .build());
    }

    private void saveJob(Problem problem, User author, boolean submission, Verdict verdict) {
        JudgeJob job = judgeJobRepository.save(JudgeJob.builder()
            .problem(problem)
            .author(author)
            .submission(submission)
            .language(Language.PYTHON)
            .code("print(1)")
            .snapshot("")
            .tokenHash("hash")
            .totalCount(1)
            .build());
        judgeJobRepository.finish(job.getId(), Verdict.QUEUED, verdict, 0, 10, "", LocalDateTime.now());
    }

    @Test
    void 토큰_없이_잔디를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/grass"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }
}
