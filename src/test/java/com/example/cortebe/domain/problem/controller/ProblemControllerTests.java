package com.example.cortebe.domain.problem.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        String location = mockMvc.perform(post("/api/problems")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.category").value("구현"))
            .andExpect(jsonPath("$.memoryLimitMb").value(256))
            .andExpect(jsonPath("$.timeLimitMs").value(1000))
            .andExpect(jsonPath("$.inputFormat").doesNotExist())
            .andExpect(jsonPath("$.outputFormat").doesNotExist())
            .andExpect(jsonPath("$.exampleInput").doesNotExist())
            .andExpect(jsonPath("$.exampleOutput").doesNotExist())
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 수의 합"));

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "수정한 문제", "category": "그래프", "timeLimitMs": 2000}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정한 문제"))
            .andExpect(jsonPath("$.description").value("두 정수의 합을 구하세요."))
            .andExpect(jsonPath("$.category").value("그래프"))
            .andExpect(jsonPath("$.memoryLimitMb").value(256))
            .andExpect(jsonPath("$.timeLimitMs").value(2000));

        mockMvc.perform(get("/api/problems").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 문제"))
            .andExpect(jsonPath("$.content[0].description").doesNotExist())
            .andExpect(jsonPath("$.content[0].category").value("그래프"))
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/problems").param("category", "구현"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/problems").param("category", "그래프"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 문제"))
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete(location))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        mockMvc.perform(get(location))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    void 잘못된_생성_요청은_입력_원문을_노출하지_않고_거절한다() throws Exception {
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
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
        mockMvc.perform(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST.replace("두 수의 합", "x".repeat(201))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_문제를_수정하거나_삭제하면_404를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/problems/999999").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
        mockMvc.perform(delete("/api/problems/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_페이지_카테고리_JSON_ID를_거절한다() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc"}) {
            mockMvc.perform(get("/api/problems?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
        mockMvc.perform(get("/api/problems").param("category", " "))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/problems").param("category", "x".repeat(51)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/problems/not-a-number"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 메모리와_시간_제한은_생성시_필수이며_양수여야_한다() throws Exception {
        for (String invalid : new String[]{"0", "-1", "null"}) {
            mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST.replace("256", invalid)))
                .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST.replace("1000", invalid)))
                .andExpect(status().isBadRequest());
        }
        for (String body : new String[]{"{\"memoryLimitMb\":0}", "{\"timeLimitMs\":-1}", "{\"category\":\" \"}"}) {
            mockMvc.perform(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST.replace("\"구현\"", "\" \"")))
            .andExpect(status().isBadRequest());
    }
}
