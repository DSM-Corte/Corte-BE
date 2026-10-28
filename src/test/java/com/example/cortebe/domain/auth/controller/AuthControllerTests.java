package com.example.cortebe.domain.auth.controller;

import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.jayway.jsonpath.JsonPath.read;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

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

    @Test
    void login() throws Exception {
        // given
        userRepository.save(new User("test@corte.com", passwordEncoder.encode("password1234"), "corte"));

        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "test@corte.com", "password": "password1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginWithWrongPassword() throws Exception {
        // given
        userRepository.save(new User("test@corte.com", passwordEncoder.encode("password1234"), "corte"));

        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "test@corte.com", "password": "wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("U002"));
    }

    @Test
    void loginWithUnknownEmail() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "none@corte.com", "password": "password1234"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("U002"));
    }

    @Test
    void 관리자_이메일로_로그인하면_ADMIN_토큰을_받는다() throws Exception {
        // given
        userRepository.save(new User("admin@corte.com", passwordEncoder.encode("password1234"), "admin"));

        // when
        String token = loginAndGetToken("admin@corte.com");

        // then
        assertThat(roleOf(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void 관리자_이메일은_대소문자가_달라도_ADMIN_토큰을_받는다() throws Exception {
        // given
        userRepository.save(new User("Admin@Corte.com", passwordEncoder.encode("password1234"), "admin"));

        // when
        String token = loginAndGetToken("Admin@Corte.com");

        // then
        assertThat(roleOf(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void 관리자_이메일이_아니면_USER_토큰을_받는다() throws Exception {
        // given
        userRepository.save(new User("test@corte.com", passwordEncoder.encode("password1234"), "corte"));

        // when
        String token = loginAndGetToken("test@corte.com");

        // then
        assertThat(roleOf(token)).isEqualTo("ROLE_USER");
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "password1234"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return read(body, "$.accessToken");
    }

    private String roleOf(String token) {
        return jwtProvider.getAuthentication(token).getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst().orElseThrow();
    }
}
