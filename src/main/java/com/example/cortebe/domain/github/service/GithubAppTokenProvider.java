package com.example.cortebe.domain.github.service;

import com.example.cortebe.domain.github.GithubAppProperties;
import com.example.cortebe.domain.github.client.GithubClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GithubAppTokenProvider {

    private static final Duration CLOCK_DRIFT = Duration.ofSeconds(60);
    private static final Duration JWT_LIFETIME = Duration.ofMinutes(9);
    private static final String JWT_HEADER = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

    private final GithubAppProperties properties;
    private final GithubClient githubClient;

    public String issueInstallationToken(long installationId) {
        String jwt = createJwt(Instant.now());
        return githubClient.createInstallationToken("Bearer " + jwt, installationId).token();
    }

    String createJwt(Instant now) {
        String payload = "{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}".formatted(
            now.minus(CLOCK_DRIFT).getEpochSecond(),
            now.plus(JWT_LIFETIME).getEpochSecond(),
            properties.clientId());
        String signingInput = base64Url(JWT_HEADER.getBytes(StandardCharsets.UTF_8))
            + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(sign(signingInput));
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("GitHub App JWT 서명에 실패했습니다.", e);
        }
    }

    private PrivateKey parsePrivateKey() {
        String pem = properties.privateKey();
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("GitHub App private key가 설정되지 않았습니다.");
        }
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException(
                "PKCS#1 형식 키입니다. openssl pkcs8 -topk8 -nocrypt 로 PKCS#8 형식으로 변환해야 합니다.");
        }
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("GitHub App private key 형식이 올바르지 않습니다.", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
