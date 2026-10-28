package com.example.cortebe.domain.judge.runner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationResultTests {
    @Test
    void 컴파일_자원초과와_실패는_컴파일오류와_사유를_반환한다() {
        // given
        for (Verdict verdict : List.of(Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT, Verdict.RUNTIME_ERROR)) {
            // when
            var result = new DockerRunner.Result(verdict, new byte[0], "진단", 10).asCompilation();
            // then
            assertThat(result.verdict()).isEqualTo(Verdict.COMPILE_ERROR);
            assertThat(result.stderr()).contains("컴파일", "진단");
            assertThat(result.runtimeMs()).isZero();
        }
    }
}
