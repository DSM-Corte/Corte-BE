package com.example.cortebe.global.swagger;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorResponseCustomizerTest.SwaggerTestController.class)
class ErrorResponseCustomizerTest {

    private static final String PRIVATE_API = "$.paths['/swagger-test/{id}'].post.responses";
    private static final String PUBLIC_API = "$.paths['/auth/swagger-test'].get.responses";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 인증_없이_API_문서를_조회한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 인증이_필요한_API에_입력_인증_던지는_공통_에러를_표시한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result
            .andExpect(jsonPath(PRIVATE_API + "['400'].content['application/json'].examples.C001").exists())
            .andExpect(jsonPath(PRIVATE_API + "['401'].content['application/json'].examples.A001").exists())
            .andExpect(jsonPath(PRIVATE_API + "['401'].content['application/json'].examples.A002").exists())
            .andExpect(jsonPath(PRIVATE_API + "['401'].content['application/json'].examples.A003").exists())
            .andExpect(jsonPath(PRIVATE_API + "['404'].content['application/json'].examples.C004").exists())
            .andExpect(jsonPath(PRIVATE_API + "['405'].content['application/json'].examples.C002").exists())
            .andExpect(jsonPath(PRIVATE_API + "['500'].content['application/json'].examples.C003").exists());
    }

    @Test
    void 공개_API에는_인증_에러와_입력_에러를_표시하지_않는다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result
            .andExpect(jsonPath(PUBLIC_API + "['200']").exists())
            .andExpect(jsonPath(PUBLIC_API + "['400']").doesNotExist())
            .andExpect(jsonPath(PUBLIC_API + "['401']").doesNotExist());
    }

    @RestController
    static class SwaggerTestController {

        @PostMapping("/swagger-test/{id}")
        String privateApi(@PathVariable Long id, @RequestBody String body) {
            throw new CorteException(GlobalErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/auth/swagger-test")
        String publicApi() {
            return "ok";
        }
    }
}
