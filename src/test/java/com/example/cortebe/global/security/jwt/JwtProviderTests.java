package com.example.cortebe.global.security.jwt;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTests {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1jb3J0ZS1iZS10ZXN0LW9ubHktMzJieXRlcw==";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30));

    @Test
    void createAndParseAccessToken() {
        String token = jwtProvider.createAccessToken(1L);

        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
    }

    @Test
    void invalidToken() {
        assertThatThrownBy(() -> jwtProvider.getUserId("invalid.token.value"))
            .isInstanceOf(CorteException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void expiredToken() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, Duration.ofMillis(-1));
        String token = expiredProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getUserId(token))
            .isInstanceOf(CorteException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
    }
}
