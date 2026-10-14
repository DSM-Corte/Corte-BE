package com.example.cortebe.global.security.jwt;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

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
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessExpiration.toMillis()))
            .signWith(secretKey)
            .compact();
    }

    public Long getUserId(String token) {
        try {
            String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
            return Long.valueOf(subject);
        } catch (ExpiredJwtException e) {
            throw new CorteException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CorteException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
