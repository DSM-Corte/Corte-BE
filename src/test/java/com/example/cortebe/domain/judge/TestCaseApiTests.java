package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.global.security.Role;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TestCaseApiTests {
    @Autowired MockMvc mvc;
    @Autowired ProblemRepository problems;
    @Autowired JwtProvider tokens;
    @Autowired ObjectMapper mapper;

    @Test
    void 관리자는_케이스를_수정_삭제하고_다른_문제의_케이스에는_접근하지_못한다() throws Exception {
        // given
        Long id = problems.save(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
        String path = "/api/problems/" + id + "/test-cases";
        String admin = "Bearer " + tokens.createAccessToken(1L, Role.ADMIN);
        String body = mvc.perform(post(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"input\":\"\",\"expectedOutput\":\"\",\"publicExample\":false,\"position\":0}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long caseId = mapper.readTree(body).path("id").asLong();
        // when & then
        mvc.perform(patch(path + "/" + caseId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedOutput\":\"3\\n\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expectedOutput").value("3\n"));
        mvc.perform(patch("/api/problems/999999/test-cases/" + caseId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete(path + "/" + caseId).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete(path + "/" + caseId).header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void 비회원_쓰기와_일반회원_쓰기를_차단하고_입력과_페이징을_검증한다() throws Exception {
        // given
        Long id = problems.save(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
        String path = "/api/problems/" + id + "/test-cases";
        // when & then
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
        mvc.perform(post(path).header("Authorization", "Bearer " + tokens.createAccessToken(1L))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + tokens.createAccessToken(1L, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(path + "?size=101")).andExpect(status().isBadRequest());
    }

    @Test
    void 비회원에게는_공개_예제만_반환한다() throws Exception {
        // given
        Long id = problems.save(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
        String path = "/api/problems/" + id + "/test-cases";
        for (boolean visible : new boolean[]{true, false}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + tokens.createAccessToken(1L, Role.ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"input\":\"1 2\\n\",\"expectedOutput\":\"3\\n\",\"publicExample\":" + visible + ",\"position\":0}"))
                .andExpect(status().isCreated());
        }
        // when & then
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].expectedOutput").value("3\n"));
        mvc.perform(get(path).header("Authorization", "Bearer " + tokens.createAccessToken(1L, Role.ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
}
