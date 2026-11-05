package com.example.cortebe.domain.workbook.controller;

import com.example.cortebe.global.security.Role;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkbookControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void 관리자가_빈_문제집을_생성하고_누구나_조회한다() throws Exception {
        // given
        String location = createWorkbook(" 스택 ");
        // when & then
        mockMvc.perform(get(location)).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("스택"))
            .andExpect(jsonPath("$.problemCount").value(0))
            .andExpect(jsonPath("$.problems.content").isEmpty());
        mockMvc.perform(head(location)).andExpect(status().isOk());
        mockMvc.perform(get("/api/workbooks")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("스택"));
        mockMvc.perform(head("/api/workbooks")).andExpect(status().isOk());
    }

    @Test
    void 목록은_최신순으로_페이징하고_문제수를_반환한다() throws Exception {
        // given
        createWorkbook("첫 문제집");
        String location = createWorkbook("둘째 문제집");
        add(location, createProblem("문제", null)).andExpect(status().isNoContent());
        // when & then
        mockMvc.perform(get("/api/workbooks?size=1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("둘째 문제집"))
            .andExpect(jsonPath("$.content[0].problemCount").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void 관리자가_이름을_수정하고_문제집을_삭제한다() throws Exception {
        // given
        String location = createWorkbook("스택");
        long problemId = createProblem("문제", null);
        add(location, problemId).andExpect(status().isNoContent());
        // when & then
        admin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" 큐 \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("큐"));
        admin(patch(location).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("큐"));
        admin(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("W001"));
        mockMvc.perform(get("/api/problems/" + problemId)).andExpect(status().isOk());
    }

    @Test
    void 같은_문제집_중복은_거부하고_다른_문제집에는_추가한다() throws Exception {
        // given
        String first = createWorkbook("스택");
        String second = createWorkbook("스택");
        long id = createProblem("문제", null);
        add(first, id).andExpect(status().isNoContent());
        // when & then
        add(first, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("W002"));
        add(second, id).andExpect(status().isNoContent());
        admin(delete(first + "/problems/" + id)).andExpect(status().isNoContent());
        admin(delete(first + "/problems/" + id)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("W003"));
        mockMvc.perform(get(second)).andExpect(jsonPath("$.problemCount").value(1));
    }

    @Test
    void 원본_문제를_삭제하면_모든_문제집에서_빠진다() throws Exception {
        // given
        String location = createWorkbook("스택");
        long id = createProblem("문제", null);
        add(location, id).andExpect(status().isNoContent());
        // when & then
        admin(delete("/api/problems/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isOk())
            .andExpect(jsonPath("$.problemCount").value(0))
            .andExpect(jsonPath("$.problems.content").isEmpty());
    }

    @Test
    void 난이도_오름차순과_동률_ID순으로_정렬하고_미지정은_마지막이다() throws Exception {
        // given
        String location = createWorkbook("탐색");
        long unrated = createProblem("미지정", null);
        long silver = createProblem("실버", "SILVER_5");
        long bronze1 = createProblem("브론즈1", "BRONZE_1");
        long bronze5 = createProblem("브론즈5", "BRONZE_5");
        long tie = createProblem("동률", "BRONZE_5");
        for (long id : new long[]{unrated, silver, bronze1, tie, bronze5}) {
            add(location, id).andExpect(status().isNoContent());
        }
        // when & then
        mockMvc.perform(get(location)).andExpect(status().isOk())
            .andExpect(jsonPath("$.problems.content.length()").value(5))
            .andExpect(jsonPath("$.problems.content[0].id").value(bronze5))
            .andExpect(jsonPath("$.problems.content[1].id").value(tie))
            .andExpect(jsonPath("$.problems.content[2].id").value(bronze1))
            .andExpect(jsonPath("$.problems.content[3].id").value(silver))
            .andExpect(jsonPath("$.problems.content[4].id").value(unrated));
        mockMvc.perform(get(location + "?page=1&size=2"))
            .andExpect(jsonPath("$.problems.content[0].id").value(bronze1))
            .andExpect(jsonPath("$.problems.totalPages").value(3))
            .andExpect(jsonPath("$.problemCount").value(5));
    }

    @Test
    void 모든_쓰기_경로는_관리자만_접근한다() throws Exception {
        // given
        String location = createWorkbook("스택");
        for (Role role : new Role[]{null, Role.USER}) {
            MockHttpServletRequestBuilder[] requests = {
                post("/api/workbooks").content("{\"name\":\"큐\"}"),
                patch(location).content("{\"name\":\"큐\"}"), delete(location),
                post(location + "/problems").content("{\"problemId\":1}"), delete(location + "/problems/1")
            };
            // when & then
            for (var request : requests) {
                request.contentType(MediaType.APPLICATION_JSON);
                if (role != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, role));
                mockMvc.perform(request).andExpect(role == null ? status().isUnauthorized() : status().isForbidden());
            }
        }
    }

    @Test
    void 잘못된_입력과_없는_자원은_계약한_오류를_반환한다() throws Exception {
        // given
        String location = createWorkbook("스택");
        // when & then
        for (String name : new String[]{"", " ", "a".repeat(101)}) {
            String body = objectMapper.writeValueAsString(java.util.Map.of("name", name));
            admin(post("/api/workbooks").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            admin(patch(location).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        for (String query : new String[]{"?page=-1", "?size=0", "?size=101", "?page=x"}) {
            mockMvc.perform(get("/api/workbooks" + query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
            mockMvc.perform(get(location + query)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/workbooks/99999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("W001"));
        add(location, 99999999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C004"));
        add(location, 0).andExpect(status().isBadRequest());
        add(location, -1).andExpect(status().isBadRequest());
    }

    private String createWorkbook(String name) throws Exception {
        return admin(post("/api/workbooks").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
            .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
            .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
    }

    @Test
    void JPA_오프셋_범위를_넘는_페이지는_400을_반환한다() throws Exception {
        // given
        String location = createWorkbook("페이지 경계");
        // when & then
        for (String query : new String[]{"?page=2147483647&size=20", "?page=21474837&size=100"}) {
            mockMvc.perform(get("/api/workbooks" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
            mockMvc.perform(get(location + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    @Test
    void JPA_최대_오프셋_범위_안의_빈_페이지는_정상_반환한다() throws Exception {
        // given
        String location = createWorkbook("페이지 경계");
        // when & then
        mockMvc.perform(get("/api/workbooks?page=2147483647&size=1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(location + "?page=2147483647&size=1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.problems.content").isEmpty());
    }

    private long createProblem(String title, String difficulty) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", title);
        body.put("description", "설명");
        body.put("category", "알고리즘");
        body.put("memoryLimitMb", 256);
        body.put("timeLimitMs", 1000);
        body.put("tags", java.util.List.of("스택"));
        if (difficulty != null) body.put("difficulty", difficulty);
        String json = admin(post("/api/problems").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private ResultActions add(String location, long id) throws Exception {
        return admin(post(location + "/problems").contentType(MediaType.APPLICATION_JSON)
            .content("{\"problemId\":" + id + "}"));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
            "Bearer " + jwtProvider.createAccessToken(1L, Role.ADMIN)));
    }
}
