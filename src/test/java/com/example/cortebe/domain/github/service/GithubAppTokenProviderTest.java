package com.example.cortebe.domain.github.service;

import com.example.cortebe.domain.github.GithubAppProperties;
import com.example.cortebe.domain.github.client.GithubClient;
import com.example.cortebe.domain.github.client.dto.GithubDto.InstallationTokenResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GithubAppTokenProviderTest {

    private static final String CLIENT_ID = "Iv23liTestClient";
    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void JWT를_RS256으로_서명하고_iat_exp_iss를_넣는다() throws Exception {
        // given
        GithubAppTokenProvider provider = providerWith(pkcs8Pem(), mock(GithubClient.class));
        Instant now = Instant.ofEpochSecond(1_800_000_000L);

        // when
        String jwt = provider.createJwt(now);

        // then
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(decode(parts[0])).isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        assertThat(decode(parts[1]))
            .isEqualTo("{\"iat\":1799999940,\"exp\":1800000540,\"iss\":\"" + CLIENT_ID + "\"}");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void 줄바꿈이_문자열_역슬래시n으로_들어온_키도_읽는다() {
        // given
        String singleLinePem = pkcs8Pem().replace("\n", "\\n");
        GithubAppTokenProvider provider = providerWith(singleLinePem, mock(GithubClient.class));

        // when
        String jwt = provider.createJwt(Instant.now());

        // then
        assertThat(jwt.split("\\.")).hasSize(3);
    }

    @Test
    void JWT로_installation_token을_발급받는다() {
        // given
        GithubClient githubClient = mock(GithubClient.class);
        given(githubClient.createInstallationToken(startsWith("Bearer ey"), eq(42L)))
            .willReturn(new InstallationTokenResponse("ghs_abc", "2026-10-14T10:00:00Z"));
        GithubAppTokenProvider provider = providerWith(pkcs8Pem(), githubClient);

        // when
        String token = provider.issueInstallationToken(42L);

        // then
        assertThat(token).isEqualTo("ghs_abc");
    }

    @Test
    void 키가_비어있으면_예외를_던진다() {
        // given
        GithubAppTokenProvider provider = providerWith("", mock(GithubClient.class));

        // when & then
        assertThatThrownBy(() -> provider.createJwt(Instant.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("설정되지 않았습니다");
    }

    @Test
    void PKCS1_키면_변환_방법을_알려주는_예외를_던진다() {
        // given
        String pkcs1Pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----";
        GithubAppTokenProvider provider = providerWith(pkcs1Pem, mock(GithubClient.class));

        // when & then
        assertThatThrownBy(() -> provider.createJwt(Instant.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("openssl pkcs8");
    }

    @Test
    void 키_형식이_깨져있으면_예외를_던진다() {
        // given
        String brokenPem = "-----BEGIN PRIVATE KEY-----\n@@not-base64@@\n-----END PRIVATE KEY-----";
        GithubAppTokenProvider provider = providerWith(brokenPem, mock(GithubClient.class));

        // when & then
        assertThatThrownBy(() -> provider.createJwt(Instant.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("형식이 올바르지 않습니다");
    }

    private GithubAppTokenProvider providerWith(String privateKey, GithubClient githubClient) {
        return new GithubAppTokenProvider(new GithubAppProperties(CLIENT_ID, privateKey), githubClient);
    }

    private static String pkcs8Pem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    private static String decode(String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }
}
