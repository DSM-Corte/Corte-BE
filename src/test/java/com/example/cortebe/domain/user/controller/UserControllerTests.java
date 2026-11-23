package com.example.cortebe.domain.user.controller;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTests {

    @Test
    void 내_티어는_점수와_다음_단계를_반환한다() throws Exception {
        // given
        User me = userRepository.save(new User("tier@corte.com", "password", "me"));
        String token = "Bearer " + jwtProvider.createAccessToken(me.getId());
        // when & then
        mockMvc.perform(get("/api/users/me/tier").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.tier").value("BRONZE_3"))
            .andExpect(jsonPath("$.nextTier").value("BRONZE_2"))
            .andExpect(jsonPath("$.pointsToNextTier").value(30));
    }

    @Test
    void 티어는_인증_없이_조회할_수_없다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/tier"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 삭제된_회원_토큰으로_티어를_조회하면_인증_오류다() throws Exception {
        // given
        String token = "Bearer " + jwtProvider.createAccessToken(Long.MAX_VALUE);
        // when & then
        mockMvc.perform(get("/api/users/me/tier").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

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

    @Test
    void GitHub_연동_정보를_저장하면_연동된_회원이_된다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        String token = adminTokenOf(me);

        // when
        mockMvc.perform(put("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"installationId": 12345, "owner": "treegus", "repo": "corte-solutions"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.installationId").value(12345))
            .andExpect(jsonPath("$.owner").value("treegus"))
            .andExpect(jsonPath("$.repo").value("corte-solutions"));

        // then
        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(me.getId()).orElseThrow();
        assertThat(saved.isGithubLinked()).isTrue();
        assertThat(saved.getGithubInstallationId()).isEqualTo(12345L);
    }

    @Test
    void GitHub_연동_정보를_다시_저장하면_덮어쓴다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        me.linkGithub(1L, "old", "old-repo");
        String token = adminTokenOf(me);

        // when
        mockMvc.perform(put("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"installationId": 2, "owner": "new", "repo": "new-repo"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owner").value("new"));

        // then
        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(me.getId()).orElseThrow();
        assertThat(saved.getGithubOwner()).isEqualTo("new");
        assertThat(saved.getGithubRepo()).isEqualTo("new-repo");
    }

    @Test
    void GitHub_연동_정보가_비었거나_올바르지_않으면_400을_준다() throws Exception {
        // given
        String token = adminTokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when & then
        for (String body : new String[] {
            "{}",
            "{\"installationId\": 0, \"owner\": \"a\", \"repo\": \"b\"}",
            "{\"installationId\": 1, \"owner\": \" \", \"repo\": \"b\"}",
            "{\"installationId\": 1, \"owner\": \"a\", \"repo\": \"\"}"
        }) {
            mockMvc.perform(put("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    @Test
    void 토큰_없이_GitHub_연동_정보를_저장하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(put("/api/users/me/github")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"installationId": 1, "owner": "a", "repo": "b"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 관리자가_아니면_GitHub_연동_정보를_저장할_수_없다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));

        // when
        mockMvc.perform(put("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, tokenOf(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"installationId": 1, "owner": "a", "repo": "b"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("A004"));

        // then
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(me.getId()).orElseThrow().isGithubLinked()).isFalse();
    }

    @Test
    void 관리자는_저장한_GitHub_연동_정보를_조회한다() throws Exception {
        // given
        String token = adminTokenOf(userRepository.save(new User("me@corte.com", "password", "me")));
        mockMvc.perform(put("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\": 7, \"owner\": \"corte\", \"repo\": \"solutions\"}"))
            .andExpect(status().isOk());

        // when & then
        mockMvc.perform(get("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.installationId").value(7))
            .andExpect(jsonPath("$.owner").value("corte"))
            .andExpect(jsonPath("$.repo").value("solutions"));
    }

    @Test
    void 연동하지_않았으면_GitHub_연동_정보가_모두_null이다() throws Exception {
        // given
        String token = adminTokenOf(userRepository.save(new User("me@corte.com", "password", "me")));

        // when & then
        mockMvc.perform(get("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.installationId").value(nullValue()))
            .andExpect(jsonPath("$.owner").value(nullValue()))
            .andExpect(jsonPath("$.repo").value(nullValue()));
    }

    @Test
    void 관리자가_아니면_GitHub_연동_정보를_조회할_수_없다() throws Exception {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));

        // when & then
        mockMvc.perform(get("/api/users/me/github").header(HttpHeaders.AUTHORIZATION, tokenOf(me)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    void 토큰_없이_GitHub_연동_정보를_조회하면_401을_준다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me/github"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    private String adminTokenOf(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId(), Role.ADMIN);
    }

    private String tokenOf(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
