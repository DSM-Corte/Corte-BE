package com.example.cortebe.global.security.jwt;

import com.example.cortebe.global.security.exception.InvalidTokenException;
import com.example.cortebe.global.security.exception.ExpiredTokenException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import com.example.cortebe.global.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
            .isInstanceOf(InvalidTokenException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void expiredToken() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, Duration.ofMillis(-1));
        String token = expiredProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getUserId(token))
            .isInstanceOf(ExpiredTokenException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void 기본_발급_토큰은_일반_사용자_권한만_가진다() {
        var authentication = jwtProvider.getAuthentication(jwtProvider.createAccessToken(1L));

        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void 서버에서_관리자로_발급한_토큰은_관리자_권한을_가진다() {
        var authentication = jwtProvider.getAuthentication(jwtProvider.createAccessToken(1L, Role.ADMIN));

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void 역할_정보가_없는_기존_토큰은_일반_사용자로_처리한다() {
        String token = Jwts.builder().subject("1")
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();

        assertThat(jwtProvider.getAuthentication(token).getAuthorities())
            .extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void 서명을_갱신하지_않고_역할을_관리자로_변조한_토큰은_거절한다() {
        String[] parts = jwtProvider.createAccessToken(1L).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace("\"USER\"", "\"ADMIN\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> jwtProvider.getAuthentication(tampered))
            .isInstanceOf(InvalidTokenException.class).extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void 알_수_없거나_잘못된_형식의_역할은_거절한다() {
        for (Object role : new Object[]{"ROOT", 123}) {
            String token = Jwts.builder().subject("1").claim("role", role)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
            assertThatThrownBy(() -> jwtProvider.getAuthentication(token))
                .isInstanceOf(InvalidTokenException.class).extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }
    }

    @Test
    void 만료된_토큰으로는_권한을_얻을_수_없다() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, Duration.ofMillis(-1));
        String token = expiredProvider.createAccessToken(1L, Role.ADMIN);

        assertThatThrownBy(() -> jwtProvider.getAuthentication(token))
            .isInstanceOf(ExpiredTokenException.class).extracting("errorCode").isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
    }
}
