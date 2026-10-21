package com.example.cortebe.global.security.jwt;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import com.example.cortebe.global.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final Duration accessExpiration;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-expiration}") Duration accessExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessExpiration = accessExpiration;
    }

    public String createAccessToken(Long userId) {
        return createAccessToken(userId, Role.USER);
    }

    public String createAccessToken(Long userId, Role role) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role.name())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessExpiration.toMillis()))
            .signWith(secretKey)
            .compact();
    }

    public Long getUserId(String token) {
        return getUserId(parseClaims(token));
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Long userId = getUserId(claims);
        try {
            String roleClaim = claims.get("role", String.class);
            Role role = roleClaim == null ? Role.USER : Role.valueOf(roleClaim);
            return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        } catch (JwtException | IllegalArgumentException e) {
            throw new CorteException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new CorteException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CorteException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CorteException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
