package com.example.cortebe.domain.problem.controller;

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
            .andExpect(status().isForbidden());
        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden());

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
}
