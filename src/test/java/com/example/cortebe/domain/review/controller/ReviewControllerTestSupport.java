package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ReviewControllerTestSupport {

    static final String CODE = "int a = 1;\nint b = 2;\nint c = 3;\n";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private JudgeJobRepository judgeJobRepository;

    @Autowired
    private JwtProvider jwtProvider;

    protected User requester;
    protected String requesterToken;
    protected String reviewerToken;
    protected String otherReviewerToken;
    protected Problem problem;

    @BeforeEach
    void setUpReviewUsers() {
        requester = saveUser("requester");
        requesterToken = bearer(requester);
        reviewerToken = bearer(saveUser("reviewer"));
        otherReviewerToken = bearer(saveUser("other"));
        problem = problemRepository.save(Problem.builder().title("문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
    }

    protected JudgeJob saveSubmission(User author, Verdict verdict) {
        JudgeJob job = JudgeJob.builder().problem(problem).author(author).submission(true).language(Language.JAVA)
            .code(CODE).snapshot("{}").tokenHash("x".repeat(64)).totalCount(1).build();
        ReflectionTestUtils.setField(job, "verdict", verdict);
        return judgeJobRepository.saveAndFlush(job);
    }

    // 요청자가 정답 제출로 리뷰를 요청하고 그 ID를 돌려준다
    protected long createReviewRequest() throws Exception {
        JudgeJob submission = saveSubmission(requester, Verdict.ACCEPTED);
        return idOf(json(post("/api/review-requests"), requesterToken, "{\"submissionId\": " + submission.getId() + "}"));
    }

    protected long createLineComment(long requestId, String body) throws Exception {
        return idOf(json(post("/api/review-requests/" + requestId + "/line-comments"), reviewerToken, body));
    }

    protected long createComment(long requestId) throws Exception {
        return idOf(json(post("/api/review-requests/" + requestId + "/comments"), reviewerToken,
            "{\"content\": \"전체 댓글\"}"));
    }

    protected ResultActions json(MockHttpServletRequestBuilder builder, String token, String body) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
