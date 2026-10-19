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
            "inputFormat": "두 정수 A와 B",
            "outputFormat": "A + B",
            "exampleInput": "1 2\\n",
            "exampleOutput": "3\\n"
        }
        """;

    @Test
    void createGetPatchListDeleteLifecycle() throws Exception {
        String location = mockMvc.perform(post("/api/problems")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.exampleInput").value("1 2\n"))
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 수의 합"));

        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "수정한 문제", "exampleInput": ""}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정한 문제"))
            .andExpect(jsonPath("$.description").value("두 정수의 합을 구하세요."))
            .andExpect(jsonPath("$.exampleInput").value(""));

        mockMvc.perform(get("/api/problems").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 문제"))
            .andExpect(jsonPath("$.content[0].description").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete(location))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        mockMvc.perform(get(location))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    void rejectsInvalidCreateWithoutEchoingInput() throws Exception {
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
    void rejectsBlankPatchAndOversizedTitle() throws Exception {
        mockMvc.perform(patch("/api/problems/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST.replace("두 수의 합", "x".repeat(201))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingProblemReturnsNotFoundOnPatchAndDelete() throws Exception {
        mockMvc.perform(patch("/api/problems/999999").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("C004"));
        mockMvc.perform(delete("/api/problems/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPaginationAndMalformedRequests() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc"}) {
            mockMvc.perform(get("/api/problems?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
        mockMvc.perform(post("/api/problems").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/problems/not-a-number"))
            .andExpect(status().isBadRequest());
    }
}
