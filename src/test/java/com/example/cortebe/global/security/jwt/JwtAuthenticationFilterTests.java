package com.example.cortebe.global.security.jwt;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTests {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1jb3J0ZS1iZS10ZXN0LW9ubHktMzJieXRlcw==";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
    private final String expiredToken = new JwtProvider(SECRET, Duration.ofMillis(-1)).createAccessToken(1L);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증_경로는_만료된_토큰이_있어도_통과시킨다() throws Exception {
        // given
        MockHttpServletRequest request = requestWithToken("/auth/login", expiredToken);
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 보호된_경로는_만료된_토큰이면_예외를_던진다() {
        // given
        MockHttpServletRequest request = requestWithToken("/problems", expiredToken);

        // when & then
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
            .isInstanceOf(CorteException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void 유효한_토큰이면_사용자_id로_인증한다() throws Exception {
        // given
        MockHttpServletRequest request = requestWithToken("/problems", jwtProvider.createAccessToken(1L));

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    private MockHttpServletRequest requestWithToken(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
