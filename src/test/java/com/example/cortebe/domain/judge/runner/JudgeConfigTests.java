package com.example.cortebe.domain.judge.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeConfigTests {
    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL,30000,5000", "60000,NULL,60000,5000", "NULL,7000,7000,7000",
        "60000,7000,60000,7000"}, nullValues = "NULL")
    void 읽기_타임아웃은_새_설정과_기존_설정과_기본값_순서로_적용한다(
        String read, String previous, int expectedRead, int expectedConnect) throws IOException {
        // given
        MockEnvironment environment = environment();
        if (read != null) environment.setProperty("JUDGE0_READ_TIMEOUT_MS", read);
        if (previous != null) environment.setProperty("JUDGE0_REQUEST_TIMEOUT_MS", previous);
        // when & then
        String prefix = "spring.cloud.openfeign.client.config.judge0.";
        assertThat(environment.getProperty(prefix + "read-timeout", Integer.class)).isEqualTo(expectedRead);
        assertThat(environment.getProperty(prefix + "connect-timeout", Integer.class)).isEqualTo(expectedConnect);
    }

    @Test
    void 전달_헤더는_기본적으로_신뢰하지_않고_명시한_배포_설정만_적용한다() throws IOException {
        // given
        MockEnvironment environment = environment();
        // when & then
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
        environment.setProperty("SERVER_FORWARD_HEADERS_STRATEGY", "native");
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }

    private MockEnvironment environment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        var properties = new YamlPropertySourceLoader().load("main",
            new FileSystemResource("src/main/resources/application.yml"));
        properties.forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
