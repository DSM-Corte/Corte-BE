package com.example.cortebe.domain.judge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionApiTests {
    @Autowired MockMvc mvc;

    @Test
    void 비회원도_인증을_요구하지_않지만_실행환경이_꺼져있으면_503이다() throws Exception {
        // when & then
        mvc.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":1,\"language\":\"PYTHON\",\"code\":\"print(3)\"}"))
            .andExpect(status().isServiceUnavailable());
    }
}
