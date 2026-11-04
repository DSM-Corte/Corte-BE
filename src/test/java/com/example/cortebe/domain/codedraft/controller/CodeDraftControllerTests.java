package com.example.cortebe.domain.codedraft.controller;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.Role;
import com.example.cortebe.global.security.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CodeDraftControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private ProblemRepository problems;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private EntityManager entityManager;

    private String token;
    private String otherToken;
    private String adminToken;
    private Long problemId;

    @BeforeEach
    void setUp() {
        token = bearer(saveUser("writer"), Role.USER);
        otherToken = bearer(saveUser("other"), Role.USER);
        adminToken = bearer(saveUser("admin"), Role.ADMIN);
        problemId = saveProblem("첫 문제").getId();
    }

    @Test
    void 처음_저장하면_201과_위치를_반환하고_DB에서_복원한다() throws Exception {
        // given
        String code = "class Main {\r\n\t// 한글 코드\n}  ";
        String path = path(problemId, "JAVA");
        // when
        save(token, path, code).andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, path))
            .andExpect(jsonPath("$.problemId").value(problemId))
            .andExpect(jsonPath("$.language").value("JAVA"))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        entityManager.flush();
        entityManager.clear();
        // then
        load(token, path).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void 기존_코드를_덮어쓰면_200과_마지막_코드를_반환한다() throws Exception {
        // given
        String path = path(problemId, "PYTHON");
        save(token, path, "print(1)").andExpect(status().isCreated());
        // when & then
        save(token, path, "print(2)").andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("print(2)"));
        entityManager.flush();
        entityManager.clear();
        load(token, path).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("print(2)"));
    }

    @Test
    void 저장하지_않은_코드는_빈_문자열과_null_시각으로_반환한다() throws Exception {
        // when & then
        load(token, path(problemId, "JAVA")).andExpect(status().isOk())
            .andExpect(jsonPath("$.problemId").value(problemId))
            .andExpect(jsonPath("$.language").value("JAVA"))
            .andExpect(jsonPath("$.code").value(""))
            .andExpect(jsonPath("$.updatedAt").isEmpty());
    }

    @Test
    void 다른_회원과_관리자는_본인_코드만_조회하고_수정한다() throws Exception {
        // given
        String path = path(problemId, "JAVA");
        save(token, path, "작성자 코드").andExpect(status().isCreated());
        // when & then
        for (String viewer : new String[]{otherToken, adminToken}) {
            load(viewer, path).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(""));
            save(viewer, path, "다른 계정 코드").andExpect(status().isCreated());
            load(token, path).andExpect(jsonPath("$.code").value("작성자 코드"));
            load(viewer, path).andExpect(jsonPath("$.code").value("다른 계정 코드"));
        }
    }

    @Test
    void 회원의_문제별_언어별_코드는_서로_분리된다() throws Exception {
        // given
        Long otherProblemId = saveProblem("다른 문제").getId();
        String[] languages = {"C", "CPP", "JAVA", "CSHARP", "JAVASCRIPT", "PYTHON"};
        // when
        for (String language : languages) {
            save(token, path(problemId, language), language + " 코드").andExpect(status().isCreated());
        }
        save(token, path(otherProblemId, "JAVA"), "다른 문제 코드").andExpect(status().isCreated());
        // then
        for (String language : languages) {
            load(token, path(problemId, language)).andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value(language)).andExpect(jsonPath("$.code").value(language + " 코드"));
        }
        load(token, path(otherProblemId, "JAVA")).andExpect(jsonPath("$.code").value("다른 문제 코드"));
    }

    @Test
    void 빈_코드와_공백만_있는_코드를_그대로_저장한다() throws Exception {
        // given
        String path = path(problemId, "JAVA");
        save(token, path, "이전 코드").andExpect(status().isCreated());
        // when & then
        for (String code : new String[]{"", " \r\n\t "}) {
            save(token, path, code).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
            load(token, path).andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void 최대_길이는_저장하고_초과하거나_null이면_기존_코드를_유지한다() throws Exception {
        // given
        String path = path(problemId, "JAVA");
        String code = "한".repeat(102400);
        save(token, path, code).andExpect(status().isCreated());
        // when & then
        save(token, path, code + "끝").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        for (String json : new String[]{"{}", "{\"code\":null}"}) {
            rawSave(token, path, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
        load(token, path).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void 인증하지_않으면_저장과_조회에_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(path(problemId, "JAVA"))).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
        mockMvc.perform(put(path(problemId, "JAVA")).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"코드\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 유효한_토큰이어도_회원이_없으면_401을_반환한다() throws Exception {
        // given
        String stale = "Bearer " + jwtProvider.createAccessToken(Long.MAX_VALUE);
        // when & then
        load(stale, path(problemId, "JAVA")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
        save(stale, path(problemId, "JAVA"), "코드").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 없는_문제는_저장과_조회에서_C004를_반환한다() throws Exception {
        // when & then
        load(token, path(Long.MAX_VALUE, "JAVA")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C004"));
        save(token, path(Long.MAX_VALUE, "JAVA"), "코드").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    void 잘못된_언어와_문제_ID는_400을_반환한다() throws Exception {
        // when & then
        for (String path : new String[]{path(problemId, "RUST"), path(0L, "JAVA"), path(-1L, "JAVA"), "/api/code-drafts/not-a-number/JAVA"}) {
            load(token, path).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
            save(token, path, "코드").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 검증과_JSON_오류는_코드_원문을_응답이나_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        // given
        String marker = "PRIVATE_DRAFT_SENTINEL_51";
        String[] requests = {
            objectMapper.writeValueAsString(Map.of("code", marker + "x".repeat(102400))),
            "{\"code\":{\"secret\":\"" + marker + "\"}}",
            "{\"code\":\"" + marker
        };
        // when & then
        for (String json : requests) {
            String response = rawSave(token, path(problemId, "JAVA"), json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001")).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain(marker);
        }
        assertThat(output.getAll()).doesNotContain(marker);
    }

    @Test
    void 원본_문제를_삭제하면_저장된_코드도_삭제한다() throws Exception {
        // given
        save(token, path(problemId, "JAVA"), "코드").andExpect(status().isCreated());
        save(otherToken, path(problemId, "PYTHON"), "다른 코드").andExpect(status().isCreated());
        // when
        mockMvc.perform(delete("/api/problems/" + problemId).header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNoContent());
        entityManager.flush();
        // then
        Number count = (Number) entityManager.createNativeQuery("select count(*) from code_drafts where problem_id=:id")
            .setParameter("id", problemId).getSingleResult();
        assertThat(count.longValue()).isZero();
        load(token, path(problemId, "JAVA")).andExpect(status().isNotFound());
    }

    private User saveUser(String name) {
        return users.save(User.builder().email(name + "@draft.test").nickname(name).password("password").build());
    }

    private Problem saveProblem(String title) {
        return problems.save(Problem.builder().title(title).description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
    }

    private String bearer(User user, Role role) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId(), role);
    }

    private String path(Long id, String language) {
        return "/api/code-drafts/" + id + "/" + language;
    }

    private ResultActions load(String auth, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth));
    }

    private ResultActions save(String auth, String path, String code) throws Exception {
        return rawSave(auth, path, objectMapper.writeValueAsString(Map.of("code", code)));
    }

    private ResultActions rawSave(String auth, String path, String json) throws Exception {
        return mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
