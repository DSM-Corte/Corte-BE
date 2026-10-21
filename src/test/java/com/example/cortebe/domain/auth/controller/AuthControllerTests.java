package com.example.cortebe.domain.auth.controller;

import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void signup() throws Exception {
        // when
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "test@corte.com", "password": "password1234", "nickname": "corte"}
                    """))
            .andExpect(status().isCreated());

        // then
        User user = userRepository.findByEmail("test@corte.com").orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo("password1234");
    }

    @Test
    void signupWithDuplicateEmail() throws Exception {
        // given
        userRepository.save(new User("test@corte.com", "password", "corte"));

        // when & then
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "test@corte.com", "password": "password1234", "nickname": "corte"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    void signupWithInvalidInput() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "not-email", "password": "short", "nickname": ""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }
}
