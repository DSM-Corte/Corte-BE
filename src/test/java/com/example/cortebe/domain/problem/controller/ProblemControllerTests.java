package com.example.cortebe.domain.problem.controller;

import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.cortebe.global.security.jwt.JwtProvider;
import com.example.cortebe.global.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void 언어별_메모리를_등록하고_부분수정과_초기화를_지원한다() throws Exception {
        // given
        String request = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"languageMemoryLimitsMb\": {\"C\":6,\"JAVA\":1024}");
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.memoryLimitMb").value(256))
            .andExpect(jsonPath("$.languageMemoryLimitsMb.C").value(6))
            .andExpect(jsonPath("$.languageMemoryLimitsMb.JAVA").value(1024))
            .andReturn().getResponse().getHeader("Location");
        // when & then
        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.languageMemoryLimitsMb.C").value(6));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"변경\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.languageMemoryLimitsMb.JAVA").value(1024));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"languageMemoryLimitsMb\":{\"PYTHON\":64}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.languageMemoryLimitsMb.PYTHON").value(64))
            .andExpect(jsonPath("$.languageMemoryLimitsMb.C").doesNotExist());
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"languageMemoryLimitsMb\":{}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.languageMemoryLimitsMb").isEmpty());
    }

    @Test
    void 언어별_메모리_범위와_키를_검증한다() throws Exception {
        // given
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        for (String invalid : new String[]{"{\"JAVA\":5}", "{\"JAVA\":1025}", "{\"JAVA\":null}", "{\"UNKNOWN\":64}"}) {
            String request = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"languageMemoryLimitsMb\":" + invalid);
            // when & then
            performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
            performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"languageMemoryLimitsMb\":" + invalid + "}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Autowired
    private ProblemRepository filterProblems;

    @Autowired
    private UserRepository filterUsers;

    @Autowired
    private JudgeJobRepository filterJobs;

    @Test
    void 등급별_다섯_단계와_미지정을_공개_조회한다() throws Exception {
        // given
        for (Difficulty difficulty : Difficulty.values()) {
            filterProblem(difficulty, "구현", Set.of("배열"));
        }
        // when & then
        for (String tier : new String[]{"BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "RUBY", "UNRATED"}) {
            mockMvc.perform(get("/api/problems").param("difficultyTier", tier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(tier.equals("UNRATED") ? 1 : 5));
        }
        mockMvc.perform(get("/api/problems"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(31));
    }

    @Test
    void 해결함은_본인의_정식_정답만_중복없이_포함한다() throws Exception {
        // given
        User me = filterUser("filter-me");
        User other = filterUser("filter-other");
        Problem solved = filterProblem(Difficulty.BRONZE_5, "구현", Set.of("배열"));
        Problem otherSolved = filterProblem(Difficulty.BRONZE_1, "구현", Set.of());
        Problem execution = filterProblem(Difficulty.SILVER_5, "구현", Set.of());
        Problem guest = filterProblem(Difficulty.GOLD_5, "구현", Set.of());
        filterJob(solved, me, true, Verdict.WRONG_ANSWER);
        filterJob(solved, me, true, Verdict.ACCEPTED);
        filterJob(solved, me, true, Verdict.ACCEPTED);
        filterJob(otherSolved, other, true, Verdict.ACCEPTED);
        filterJob(execution, me, false, Verdict.ACCEPTED);
        filterJob(guest, null, true, Verdict.ACCEPTED);
        // when & then
        mockMvc.perform(get("/api/problems").param("solveStatus", "SOLVED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(me.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(solved.getId()));
        mockMvc.perform(get("/api/problems").param("solveStatus", "SOLVED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(other.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(otherSolved.getId()));
    }

    @Test
    void 미해결은_오답과_미제출을_포함하고_정답은_제외한다() throws Exception {
        // given
        User me = filterUser("unsolved-me");
        Problem solved = filterProblem(Difficulty.BRONZE_5, "구현", Set.of());
        Problem wrong = filterProblem(Difficulty.BRONZE_1, "구현", Set.of());
        Problem untouched = filterProblem(Difficulty.SILVER_5, "구현", Set.of());
        filterJob(solved, me, true, Verdict.ACCEPTED);
        filterJob(wrong, me, true, Verdict.WRONG_ANSWER);
        // when & then
        mockMvc.perform(get("/api/problems").param("solveStatus", "UNSOLVED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(me.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(untouched.getId()))
            .andExpect(jsonPath("$.content[1].id").value(wrong.getId()));
    }

    @Test
    void 모든_필터를_결합하고_페이징과_최신순을_유지한다() throws Exception {
        // given
        User me = filterUser("combined-me");
        Problem first = filterProblem(Difficulty.BRONZE_5, "구현", Set.of("배열"));
        Problem second = filterProblem(Difficulty.BRONZE_1, "구현", Set.of("배열"));
        filterProblem(Difficulty.SILVER_5, "구현", Set.of("배열"));
        filterProblem(Difficulty.BRONZE_3, "그래프", Set.of("배열"));
        filterProblem(Difficulty.BRONZE_2, "구현", Set.of("정렬"));
        filterJob(first, me, true, Verdict.ACCEPTED);
        filterJob(second, me, true, Verdict.ACCEPTED);
        filterJob(second, me, true, Verdict.ACCEPTED);
        // when & then
        mockMvc.perform(get("/api/problems").param("difficultyTier", "BRONZE").param("solveStatus", "SOLVED")
                .param("category", "구현").param("tag", "배열").param("size", "1").param("page", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(me.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content[0].id").value(first.getId()));
        mockMvc.perform(get("/api/problems").param("difficultyTier", "RUBY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 개인_상태_필터는_로그인과_존재하는_회원을_요구한다() throws Exception {
        // when & then
        for (String solveStatus : new String[]{"SOLVED", "UNSOLVED"}) {
            mockMvc.perform(get("/api/problems").param("solveStatus", solveStatus))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
            mockMvc.perform(get("/api/problems").param("solveStatus", solveStatus)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(999999L)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
        }
        mockMvc.perform(get("/api/problems").param("solveStatus", "ALL"))
            .andExpect(status().isOk());
    }

    @Test
    void 잘못된_필터값은_400을_반환한다() throws Exception {
        // when & then
        for (String tier : new String[]{"BRONZE_5", "UNKNOWN", "bronze", " "}) {
            mockMvc.perform(get("/api/problems").param("difficultyTier", tier))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
        for (String solveStatus : new String[]{"UNKNOWN", "solved", " "}) {
            mockMvc.perform(get("/api/problems").param("solveStatus", solveStatus))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
    }

    private Problem filterProblem(Difficulty difficulty, String category, Set<String> tags) {
        return filterProblems.saveAndFlush(Problem.builder().title(difficulty.name()).description("필터 테스트")
            .difficulty(difficulty).category(category).tags(tags).memoryLimitMb(256).timeLimitMs(1000).build());
    }

    private User filterUser(String name) {
        return filterUsers.saveAndFlush(User.builder().email(name + "@corte.com").password("password").nickname(name).build());
    }

    private void filterJob(Problem problem, User user, boolean submission, Verdict verdict) {
        JudgeJob job = JudgeJob.builder().problem(problem).author(user).submission(submission)
            .language(Language.PYTHON).code("print(1)").snapshot("{}").tokenHash("filter-token").totalCount(1).build();
        ReflectionTestUtils.setField(job, "verdict", verdict);
        filterJobs.saveAndFlush(job);
    }

    private static final String CREATE_REQUEST = """
        {
            "title": "두 수의 합",
            "description": "두 정수의 합을 구하세요.",
            "category": "구현",
            "memoryLimitMb": 256,
            "timeLimitMs": 1000
        }
        """;

    @Test
    void 문제_생성_조회_부분수정_목록조회_삭제가_동작한다() throws Exception {
        String location = performAsAdmin(post("/api/problems")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.category").value("구현"))
            .andExpect(jsonPath("$.memoryLimitMb").value(256))
            .andExpect(jsonPath("$.timeLimitMs").value(1000))
            .andExpect(jsonPath("$.tags").isEmpty())
            .andExpect(jsonPath("$.inputFormat").doesNotExist())
            .andExpect(jsonPath("$.outputFormat").doesNotExist())
            .andExpect(jsonPath("$.exampleInput").doesNotExist())
            .andExpect(jsonPath("$.exampleOutput").doesNotExist())
            .andReturn().getResponse().getHeader("Location");

        performAsAdmin(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 수의 합"));

        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "수정한 문제", "category": "그래프", "timeLimitMs": 2000}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정한 문제"))
            .andExpect(jsonPath("$.description").value("두 정수의 합을 구하세요."))
            .andExpect(jsonPath("$.category").value("그래프"))
            .andExpect(jsonPath("$.memoryLimitMb").value(256))
            .andExpect(jsonPath("$.timeLimitMs").value(2000));

        performAsAdmin(get("/api/problems").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 문제"))
            .andExpect(jsonPath("$.content[0].description").doesNotExist())
            .andExpect(jsonPath("$.content[0].category").value("그래프"))
            .andExpect(jsonPath("$.totalElements").value(1));

        performAsAdmin(get("/api/problems").param("category", "구현"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
        performAsAdmin(get("/api/problems").param("category", "그래프"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 문제"))
            .andExpect(jsonPath("$.totalElements").value(1));

        performAsAdmin(delete(location))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        performAsAdmin(get(location))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    void 잘못된_생성_요청은_입력_원문을_노출하지_않고_거절한다() throws Exception {
        performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":" ","description":"private submitted content"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].value").isEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private submitted content"))));
    }

    @Test
    void 공백_제목_수정과_길이_제한을_넘는_제목_생성을_거절한다() throws Exception {
        performAsAdmin(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest());
        performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST.replace("두 수의 합", "x".repeat(201))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_문제를_수정하거나_삭제하면_404를_반환한다() throws Exception {
        performAsAdmin(patch("/api/problems/999999").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
        performAsAdmin(delete("/api/problems/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_페이지_카테고리_JSON_ID를_거절한다() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc"}) {
            performAsAdmin(get("/api/problems?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
        performAsAdmin(get("/api/problems").param("category", " "))
            .andExpect(status().isBadRequest());
        performAsAdmin(get("/api/problems").param("category", "x".repeat(51)))
            .andExpect(status().isBadRequest());
        performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        performAsAdmin(get("/api/problems/not-a-number"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 메모리와_시간_제한은_생성시_필수이며_양수여야_한다() throws Exception {
        for (String invalid : new String[]{"0", "-1", "null"}) {
            performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST.replace("256", invalid)))
                .andExpect(status().isBadRequest());
            performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST.replace("1000", invalid)))
                .andExpect(status().isBadRequest());
        }
        for (String body : new String[]{"{\"memoryLimitMb\":0}", "{\"timeLimitMs\":-1}", "{\"category\":\" \"}"}) {
            performAsAdmin(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST.replace("\"구현\"", "\" \"")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 여러_태그를_생성하고_태그와_카테고리로_조회하며_태그를_수정_삭제한다() throws Exception {
        String taggedRequest = CREATE_REQUEST.replace("\"timeLimitMs\": 1000",
            "\"timeLimitMs\": 1000, \"tags\": [\" 배열 \", \"배열\", \"정렬\"]");
        String location = performAsAdmin(post("/api/problems")
                .contentType(MediaType.APPLICATION_JSON).content(taggedRequest))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.containsInAnyOrder("배열", "정렬")))
            .andReturn().getResponse().getHeader("Location");

        performAsAdmin(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.containsInAnyOrder("배열", "정렬")));
        performAsAdmin(get("/api/problems").param("tag", " 배열 ").param("category", "구현"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].tags", org.hamcrest.Matchers.containsInAnyOrder("배열", "정렬")));
        performAsAdmin(get("/api/problems").param("tag", "배열").param("category", "그래프"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        performAsAdmin(get("/api/problems").param("tag", "없는태그"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());

        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.containsInAnyOrder("배열", "정렬")));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"tags\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.containsInAnyOrder("배열", "정렬")));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[\"탐색\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0]").value("탐색"));
        performAsAdmin(get("/api/problems").param("tag", "배열"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isEmpty());
        performAsAdmin(get("/api/problems"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].tags").isEmpty());
    }

    @Test
    void 태그의_빈값_길이_개수_제한을_검증한다() throws Exception {
        String tooMany = IntStream.range(0, 11).mapToObj(i -> "\"tag" + i + "\"")
            .collect(Collectors.joining(",", "[", "]"));
        for (String tags : new String[]{"[\" \"]", "[null]", "[\"" + "x".repeat(31) + "\"]", tooMany}) {
            performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"tags\":" + tags)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
            performAsAdmin(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tags\":" + tags + "}"))
                .andExpect(status().isBadRequest());
        }
        for (String tag : new String[]{"", " ", "x".repeat(31)}) {
            performAsAdmin(get("/api/problems").param("tag", tag))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    @Test
    void 인증하지_않아도_목록과_상세를_조회할_수_있다() throws Exception {
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get("/api/problems").param("category", "구현"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 수의 합"));
        mockMvc.perform(head(location))
            .andExpect(status().isOk());
    }

    @Test
    void 일반_사용자는_조회할_수_있지만_생성_수정_삭제할_수_없다() throws Exception {
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        String token = "Bearer " + jwtProvider.createAccessToken(2L);

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/problems").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("A004"))
            .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("A004"));
        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("A004"));

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 수의 합"));
        mockMvc.perform(get("/api/problems"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 비로그인_사용자의_생성_수정_삭제는_인증_오류를_반환한다() throws Exception {
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
        mockMvc.perform(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/problems/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_역할_헤더나_본문만으로_쓰기_권한을_얻을_수_없다() throws Exception {
        String body = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"role\":\"ADMIN\"");
        mockMvc.perform(post("/api/problems").header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + jwtProvider.createAccessToken(2L)).header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
    }

    private ResultActions performAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
            "Bearer " + jwtProvider.createAccessToken(1L, Role.ADMIN)));
    }

    @Test
    void 기본_난이도는_미지정이고_관리자가_선택하거나_초기화한다() throws Exception {
        // given
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"tags\":[\"난이도테스트\"]")))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        // when & then
        mockMvc.perform(get(location)).andExpect(jsonPath("$.difficulty").value("UNRATED"));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"GOLD_1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value("GOLD_1"));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value("GOLD_1"));
        mockMvc.perform(get("/api/problems?tag=난이도테스트")).andExpect(jsonPath("$.content[0].difficulty").value("GOLD_1"));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"UNRATED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value("UNRATED"));
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"GOLD_0\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 잘못된_JSON_타입의_난이도는_생성과_수정에서_거부한다() throws Exception {
        // given
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        // when & then
        for (String value : new String[]{"0", "\"0\"", "true", "\"\"", "[]", "{}"}) {
            String request = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"difficulty\":" + value);
            performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
            performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":" + value + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
        mockMvc.perform(get(location)).andExpect(jsonPath("$.difficulty").value("UNRATED"));
    }

    @Test
    void 생성시_선택과_null_기본값을_지원하고_일반_카테고리_목록에도_반영한다() throws Exception {
        // given
        String request = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"difficulty\":\"SILVER_3\"");
        String location = performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.difficulty").value("SILVER_3"))
            .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        // when & then
        performAsAdmin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value("SILVER_3"));
        mockMvc.perform(get("/api/problems")).andExpect(jsonPath("$.content[0].difficulty").value("SILVER_3"));
        mockMvc.perform(get("/api/problems").param("category", "구현"))
            .andExpect(jsonPath("$.content[0].difficulty").value("SILVER_3"));
        String nullRequest = CREATE_REQUEST.replace("\"timeLimitMs\": 1000", "\"timeLimitMs\": 1000, \"difficulty\":null");
        performAsAdmin(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content(nullRequest))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.difficulty").value("UNRATED"));
    }
}
