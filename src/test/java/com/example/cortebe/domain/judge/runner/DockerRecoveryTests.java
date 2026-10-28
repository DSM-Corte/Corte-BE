package com.example.cortebe.domain.judge.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

class DockerRecoveryTests {
    @Test
    void 이전_컨테이너_삭제가_실패하면_새_실행을_차단한다() throws Exception {
        // given
        DockerRunner runner = runner();
        Process listing = process("old-container\n", 0, false);
        Process removal = process("", 1, false);
        try (var ignored = mockConstruction(ProcessBuilder.class, (builder, context) -> {
            List<?> args = (List<?>) context.arguments().getFirst();
            given(builder.start()).willReturn(args.contains("ps") ? listing : removal);
        })) {
            // when
            runner.recoverContainers();
            // then
            assertThat(runner.available()).isFalse();
        }
    }

    @Test
    void 신뢰_진입점_준비중_취소도_서버오류가_아닌_취소로_유지한다() throws Exception {
        // given
        AtomicBoolean cancelled = new AtomicBoolean();
        try (var ignored = mockConstruction(ProcessBuilder.class, (builder, context) -> {
            List<?> args = (List<?>) context.arguments().getFirst();
            boolean bootstrap = args.contains("javac") && args.contains("/work/Entry.java");
            Process process = process("", 0, bootstrap);
            given(builder.start()).willAnswer(invocation -> { if (bootstrap) cancelled.set(true); return process; });
        }); var program = runner().prepare(Language.JAVA, "class Main {}", cancelled)) {
            // when & then
            assertThat(program.compilation().verdict()).isEqualTo(Verdict.CANCELLED);
        }
    }

    private DockerRunner runner() {
        return new DockerRunner(new JudgeProperties(true, "docker", 2, 16, 30, 120, 60, 1048576));
    }

    private Process process(String output, int exitCode, boolean waitForCancellation) throws Exception {
        Process process = mock(Process.class);
        given(process.getInputStream()).willReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        given(process.getErrorStream()).willReturn(new ByteArrayInputStream(new byte[0]));
        given(process.getOutputStream()).willReturn(new ByteArrayOutputStream());
        given(process.waitFor(anyLong(), any(TimeUnit.class))).willAnswer(invocation ->
            !waitForCancellation || invocation.getArgument(1) != TimeUnit.MILLISECONDS);
        given(process.exitValue()).willReturn(exitCode);
        return process;
    }
}
