package com.example.cortebe.domain.judge.runner;

import com.example.cortebe.domain.judge.exception.JudgeUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class DockerRunner {
    private static final String PROJECT = """
        <Project Sdk="Microsoft.NET.Sdk">
          <PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework>
          <EnableDefaultCompileItems>false</EnableDefaultCompileItems><UseAppHost>false</UseAppHost>
          <UseSharedCompilation>false</UseSharedCompilation></PropertyGroup>
          <ItemGroup><Compile Include="/work/Main.cs" /></ItemGroup>
        </Project>
        """;
    private final JudgeProperties properties;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public boolean available() { return properties.enabled() && healthy.get(); }

    public void recoverContainers() {
        if (!properties.enabled()) return;
        try {
            String names = command(List.of("ps", "-aq", "--filter", "label=corte.runner=true"));
            for (String id : names.lines().filter(value -> !value.isBlank()).toList()) {
                command(List.of("rm", "-f", id.strip()));
            }
            healthy.set(true);
        } catch (Exception exception) {
            healthy.set(false);
            log.error("이전 실행 컨테이너를 정리할 수 없어 새 실행을 차단합니다.", exception);
        }
    }

    public record Result(Verdict verdict, byte[] stdout, String stderr, long runtimeMs) { }

    public interface Input {
        void write(byte[] bytes) throws IOException;
        void eof() throws IOException;
        void cancel();
    }

    public Result execute(Language language, String code, int memoryMb, long timeMs, byte[] stdin,
                          Consumer<Input> ready, BiConsumer<String, byte[]> output, AtomicBoolean cancelled) {
        if (!available()) throw new JudgeUnavailableException();
        String name = "corte-" + UUID.randomUUID();
        try {
            if (cancelled.get()) return new Result(Verdict.CANCELLED, new byte[0], "", 0);
            command(List.of("create", "--pull", "never", "--name", name, "--label", "corte.runner=true", "--network", "none",
                "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                "--user", "65534:65534", "--pids-limit", "128", "--cpus", "1",
                "--memory", "768m", "--memory-swap", "768m",
                "--ulimit", "nofile=1024:1024", "--ulimit", "core=0:0",
                "--tmpfs", "/work:rw,exec,nosuid,size=134217728,mode=1777",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=67108864,mode=1777",
                "-e", "HOME=/work", "-e", "DOTNET_CLI_HOME=/work", "-e", "DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1",
                "-e", "XDG_RUNTIME_DIR=/work/runtime",
                "-e", "DOTNET_CLI_TELEMETRY_OPTOUT=1", "-e", "DOTNET_NOLOGO=1",
                "-w", "/work", language.image(), "sleep", "infinity"));
            command(List.of("start", name));
            upload(name, language.filename(), code);
            if (language == Language.CSHARP) {
                upload(name, "Judge.csproj", PROJECT);
            }
            Result compilation = run(name, language.compileCommand(), properties.compileSeconds() * 1000L,
                new byte[0], ignored -> { }, (type, bytes) -> { }, cancelled);
            if (compilation.verdict() != Verdict.FINISHED) {
                if (compilation.verdict() == Verdict.RUNTIME_ERROR) {
                    return new Result(Verdict.COMPILE_ERROR, compilation.stdout(), compilation.stderr(), compilation.runtimeMs());
                }
                return compilation;
            }
            if (cancelled.get()) return new Result(Verdict.CANCELLED, new byte[0], "", 0);
            command(List.of("update", "--memory", memoryMb + "m", "--memory-swap", memoryMb + "m", name));
            return run(name, language.runCommand(), timeMs, stdin, ready, output, cancelled);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(Verdict.CANCELLED, new byte[0], "", 0);
        } catch (Exception exception) {
            log.error("격리 실행 환경 처리에 실패했습니다.", exception);
            return new Result(Verdict.SYSTEM_ERROR, new byte[0], "실행 환경 오류가 발생했습니다.", 0);
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                command(List.of("rm", "-f", name));
            } catch (Exception exception) {
                healthy.set(false);
                log.error("실행 컨테이너 정리에 실패했습니다: {}", name, exception);
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private Result run(String name, List<String> args, long timeoutMs, byte[] stdin, Consumer<Input> ready,
                       BiConsumer<String, byte[]> output, AtomicBoolean cancelled) throws Exception {
        List<String> command = new ArrayList<>(List.of("exec", "-i", name));
        command.addAll(args);
        Process process = start(command);
        AtomicInteger count = new AtomicInteger();
        AtomicBoolean exceeded = new AtomicBoolean();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread out = pump(process.getInputStream(), stdout, "stdout", count, exceeded, output);
        Thread err = pump(process.getErrorStream(), stderr, "stderr", count, exceeded, output);
        OutputStream input = process.getOutputStream();
        Input control = new Input() {
            public synchronized void write(byte[] bytes) throws IOException { input.write(bytes); input.flush(); }
            public synchronized void eof() throws IOException { input.close(); }
            public void cancel() { cancelled.set(true); }
        };
        long start = System.nanoTime();
        Thread writer = null;
        Verdict verdict = Verdict.FINISHED;
        try {
            if (stdin == null) ready.accept(control);
            else writer = Thread.startVirtualThread(() -> {
                try { control.write(stdin); control.eof(); } catch (IOException ignored) { }
            });
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                if (cancelled.get()) { verdict = Verdict.CANCELLED; break; }
                if (exceeded.get()) { verdict = Verdict.OUTPUT_LIMIT; break; }
                if (Duration.ofNanos(System.nanoTime() - start).toMillis() >= timeoutMs) {
                    verdict = Verdict.TIME_LIMIT; break;
                }
            }
            if (verdict != Verdict.FINISHED) command(List.of("kill", name));
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            out.join(2000);
            err.join(2000);
            if (out.isAlive() || err.isAlive()) {
                command(List.of("kill", name));
                process.getInputStream().close();
                process.getErrorStream().close();
                if (verdict == Verdict.FINISHED) verdict = Verdict.RUNTIME_ERROR;
            }
            if (exceeded.get()) verdict = Verdict.OUTPUT_LIMIT;
            if (verdict == Verdict.FINISHED && process.exitValue() != 0) {
                String oom = command(List.of("inspect", "--format", "{{.State.OOMKilled}}", name)).strip();
                if (!oom.equals("true")) {
                    String events = command(List.of("exec", name, "cat", "/sys/fs/cgroup/memory.events"));
                    oom = events.lines().anyMatch(line -> line.startsWith("oom_kill ") && !line.endsWith(" 0")) ? "true" : "false";
                }
                verdict = oom.equals("true") ? Verdict.MEMORY_LIMIT : Verdict.RUNTIME_ERROR;
            }
            return new Result(verdict, stdout.toByteArray(), stderr.toString(StandardCharsets.UTF_8),
                Duration.ofNanos(System.nanoTime() - start).toMillis());
        } finally {
            input.close();
            if (writer != null) writer.interrupt();
            process.destroyForcibly();
        }
    }

    private Thread pump(InputStream stream, ByteArrayOutputStream captured, String type, AtomicInteger count,
                        AtomicBoolean exceeded, BiConsumer<String, byte[]> output) {
        return Thread.startVirtualThread(() -> {
            try (stream) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (count.addAndGet(size) > properties.outputBytes()) { exceeded.set(true); break; }
                    byte[] bytes = java.util.Arrays.copyOf(buffer, size);
                    synchronized (captured) { captured.writeBytes(bytes); }
                    output.accept(type, bytes);
                }
            } catch (IOException exception) {
                log.debug("실행 출력 스트림이 닫혔습니다.", exception);
            }
        });
    }

    private Process start(List<String> args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(properties.docker());
        command.addAll(args);
        return new ProcessBuilder(command).start();
    }

    private void upload(String name, String filename, String content) throws IOException, InterruptedException {
        // 파일 이름은 언어 enum의 상수이며 사용자 소스는 명령 문자열이 아닌 stdin으로 전달한다.
        Process process = start(List.of("exec", "-i", name, "sh", "-c", "cat > /work/" + filename));
        try {
            try (OutputStream stream = process.getOutputStream()) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("컨테이너 소스 전달에 실패했습니다.");
            }
        } finally {
            process.destroyForcibly();
        }
    }

    private String command(List<String> args) throws IOException, InterruptedException {
        Process process = start(args);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread out = Thread.startVirtualThread(() -> copyLimited(process.getInputStream(), captured));
        Thread err = Thread.startVirtualThread(() -> copyLimited(process.getErrorStream(), captured));
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw new IOException("Docker 명령 시간이 초과되었습니다.");
            out.join(1000);
            err.join(1000);
            if (process.exitValue() != 0) throw new IOException("Docker 명령 실패: " + captured.toString(StandardCharsets.UTF_8));
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            process.destroyForcibly();
        }
    }

    private void copyLimited(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] bytes = input.readNBytes(16384);
            synchronized (output) { output.writeBytes(bytes); }
        } catch (IOException ignored) { }
    }

}
