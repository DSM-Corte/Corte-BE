package com.example.cortebe.domain.judge;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionApiTests {
    @Autowired MockMvc mvc;
    @Autowired JudgeJobRepository jobs;
    @Autowired ProblemRepository problems;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwtProvider;

    @Test
    void 비회원도_인증을_요구하지_않지만_실행환경이_꺼져있으면_503이다() throws Exception {
        // when & then
        mvc.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":1,\"language\":\"PYTHON\",\"code\":\"print(3)\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 내_제출_목록을_최신순으로_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        User other = saveUser("other");
        Problem problem = saveProblem();
        JudgeJob older = saveSubmission(problem, me);
        JudgeJob newer = saveSubmission(problem, me);
        saveSubmission(problem, other);

        // when & then
        mvc.perform(get("/api/submissions").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
            .andExpect(jsonPath("$.content[1].id").value(older.getId()))
            .andExpect(jsonPath("$.content[0].problemId").value(problem.getId()))
            .andExpect(jsonPath("$.content[0].problemTitle").value("합"))
            .andExpect(jsonPath("$.content[0].language").value("PYTHON"))
            .andExpect(jsonPath("$.content[0].verdict").value("QUEUED"))
            .andExpect(jsonPath("$.content[0].code").doesNotExist())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 토큰_없이_제출_목록을_조회하면_401_A001을_반환한다() throws Exception {
        // when & then
        mvc.perform(get("/api/submissions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 페이지_크기가_100을_넘으면_400_C001을_반환한다() throws Exception {
        // given
        User me = saveUser("me");

        // when & then
        mvc.perform(get("/api/submissions").param("size", "101").header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 제출_단건_조회는_코드와_언어를_함께_반환한다() throws Exception {
        // given
        User me = saveUser("me");
        Problem problem = saveProblem();
        JudgeJob job = saveSubmission(problem, me);

        // when & then
        mvc.perform(get("/api/submissions/" + job.getId()).header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.problemId").value(problem.getId()))
            .andExpect(jsonPath("$.language").value("PYTHON"))
            .andExpect(jsonPath("$.code").value("print(3)"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    private JudgeJob saveSubmission(Problem problem, User author) {
        return jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(author).submission(true)
            .language(Language.PYTHON).code("print(3)").snapshot("{}").tokenHash("hash").totalCount(1).build());
    }

    private Problem saveProblem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build());
    }

    private User saveUser(String nickname) {
        return users.saveAndFlush(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
