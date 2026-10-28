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
import java.util.ArrayList;
import java.util.Arrays;
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
            boolean cleaned = true;
            for (String id : names.lines().filter(value -> !value.isBlank()).toList()) cleaned &= remove(id.strip());
            healthy.set(cleaned);
        } catch (Exception exception) {
            healthy.set(false);
            log.error("이전 실행 환경을 정리할 수 없어 새 실행을 차단합니다.", exception);
        }
    }

    public record Result(Verdict verdict, byte[] stdout, String stderr, long runtimeMs) {
        public Result asCompilation() {
            String reason = switch (verdict) {
                case TIME_LIMIT -> "컴파일 시간 제한을 초과했습니다.";
                case MEMORY_LIMIT -> "컴파일 메모리 제한을 초과했습니다.";
                case OUTPUT_LIMIT -> "컴파일 출력 제한을 초과했습니다.";
                case RUNTIME_ERROR -> "컴파일에 실패했습니다.";
                default -> null;
            };
            return reason == null ? new Result(verdict, stdout, stderr, 0)
                : new Result(Verdict.COMPILE_ERROR, stdout, reason + "\n" + stderr, 0);
        }
    }

    public interface Input {
        void write(byte[] bytes) throws IOException;
        void eof() throws IOException;
        void cancel();
    }

    public PreparedProgram prepare(Language language, String code, AtomicBoolean cancelled) {
        if (!available()) throw new JudgeUnavailableException();
        String name = "corte-" + UUID.randomUUID();
        Result compilation;
        try {
            if (cancelled.get()) return new PreparedProgram(null, language, cancelled());
            create(name, language, 768);
            upload(name, language.filename(), code);
            if (language == Language.CSHARP) upload(name, "Judge.csproj", PROJECT);
            compilation = run(name, language.compileCommand(), properties.compileSeconds() * 1000L,
                new byte[0], ignored -> { }, (type, bytes) -> { }, cancelled, null).asCompilation();
            if (compilation.verdict() == Verdict.FINISHED) {
                prepareEntrypoint(name, language, cancelled);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            compilation = cancelled();
        } catch (Exception exception) {
            if (cancelled.get()) compilation = cancelled();
            else {
                log.error("컴파일 환경 준비에 실패했습니다.", exception);
                compilation = systemError();
            }
        }
        return new PreparedProgram(name, language, compilation);
    }

    public Result execute(Language language, String code, int memoryMb, long timeMs, byte[] stdin,
                          Consumer<Input> ready, BiConsumer<String, byte[]> output, AtomicBoolean cancelled) {
        try (PreparedProgram program = prepare(language, code, cancelled)) {
            return program.execute(memoryMb, timeMs, stdin, ready, output, cancelled);
        }
    }

    public final class PreparedProgram implements AutoCloseable {
        private final String compiler;
        private final Language language;
        private final Result compilation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PreparedProgram(String compiler, Language language, Result compilation) {
            this.compiler = compiler;
            this.language = language;
            this.compilation = compilation;
        }

        public Result compilation() { return compilation; }

        public Result execute(int memoryMb, long timeMs, byte[] stdin, Consumer<Input> ready,
                              BiConsumer<String, byte[]> output, AtomicBoolean cancelled) {
            if (closed.get()) throw new IllegalStateException("종료된 컴파일 결과는 사용할 수 없습니다.");
            if (compilation.verdict() != Verdict.FINISHED) return compilation;
            if (cancelled.get()) return cancelled();
            String target = "corte-" + UUID.randomUUID();
            try {
                // 케이스마다 새 cgroup과 파일 시스템을 사용하여 이전 입력·프로세스가 남지 않게 한다.
                create(target, language, memoryMb);
                transfer(compiler, target);
                String marker = "CORTE-ENTRY-" + UUID.randomUUID();
                return run(target, TrustedEntrypoints.command(language, marker), timeMs, stdin, ready, output, cancelled, marker);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return cancelled();
            } catch (Exception exception) {
                if (oom(target)) return new Result(Verdict.MEMORY_LIMIT, new byte[0], "", 0);
                log.error("실행 환경 준비에 실패했습니다.", exception);
                return systemError();
            } finally { remove(target); }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true) && compiler != null) remove(compiler);
        }
    }

    private void prepareEntrypoint(String name, Language language, AtomicBoolean cancelled) throws Exception {
        if (language == Language.JAVASCRIPT || language == Language.PYTHON) {
            command(List.of("exec", name, "cp", "/work/" + language.filename(), "/work/program/" + language.filename()));
        } else if (language == Language.JAVA || language == Language.CSHARP) {
            List<String> args;
            if (language == Language.JAVA) {
                upload(name, "Entry.java", TrustedEntrypoints.JAVA);
                args = List.of("javac", "-d", "/work/bootstrap", "/work/Entry.java");
            } else {
                upload(name, "Bootstrap.cs", TrustedEntrypoints.CSHARP);
                upload(name, "Bootstrap.csproj", TrustedEntrypoints.BOOTSTRAP_PROJECT);
                args = List.of("dotnet", "build", "/work/Bootstrap.csproj", "-o", "/work/bootstrap", "--nologo", "--ignore-failed-sources");
            }
            Result result = run(name, args, properties.compileSeconds() * 1000L, new byte[0],
                ignored -> { }, (type, bytes) -> { }, cancelled, null);
            if (result.verdict() != Verdict.FINISHED) throw new IOException("신뢰된 실행 진입점을 준비할 수 없습니다: " + result.stderr());
        }
    }

    private void create(String name, Language language, int memoryMb) throws Exception {
        command(List.of("create", "--pull", "never", "--name", name, "--label", "corte.runner=true", "--network", "none",
            "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
            "--user", "65534:65534", "--pids-limit", "128", "--cpus", "1",
            "--memory", memoryMb + "m", "--memory-swap", memoryMb + "m",
            "--ulimit", "nofile=1024:1024", "--ulimit", "core=0:0",
            "--tmpfs", "/work:rw,exec,nosuid,size=134217728,mode=1777",
            "--tmpfs", "/tmp:rw,noexec,nosuid,size=67108864,mode=1777",
            "-e", "HOME=/work", "-e", "DOTNET_CLI_HOME=/work", "-e", "DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1",
            "-e", "XDG_RUNTIME_DIR=/work/runtime", "-e", "DOTNET_CLI_TELEMETRY_OPTOUT=1", "-e", "DOTNET_NOLOGO=1",
            "-w", "/work", language.image(), "sleep", "infinity"));
        command(List.of("start", name));
        command(List.of("exec", name, "mkdir", "-m700", "/work/program", "/work/bootstrap", "/work/runtime"));
    }

    private void transfer(String source, String target) throws Exception {
        // tar는 컨테이너 사이의 파이프로만 전달한다. 호스트 파일로 추출하지 않는다.
        List<Process> pipeline = ProcessBuilder.startPipeline(List.of(
            builder(List.of("exec", source, "tar", "-C", "/work", "-cf", "-", "program", "bootstrap")),
            builder(List.of("exec", "-i", target, "tar", "-C", "/work", "-xf", "-"))));
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        List<Thread> readers = new ArrayList<>();
        pipeline.forEach(process -> readers.add(Thread.startVirtualThread(() -> copyLimited(process.getErrorStream(), errors))));
        readers.add(Thread.startVirtualThread(() -> copyLimited(pipeline.getLast().getInputStream(), errors)));
        try {
            for (Process process : pipeline) {
                if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    throw new IOException("컴파일 결과 전달에 실패했습니다.");
                }
            }
            for (Thread reader : readers) reader.join(1000);
        } finally { pipeline.forEach(Process::destroyForcibly); }
    }

    private Result run(String name, List<String> args, long timeoutMs, byte[] stdin, Consumer<Input> ready,
                       BiConsumer<String, byte[]> output, AtomicBoolean cancelled, String marker) throws Exception {
        List<String> command = new ArrayList<>(List.of("exec", "-i", name));
        command.addAll(args);
        Process process = builder(command).start();
        AtomicInteger count = new AtomicInteger();
        AtomicBoolean exceeded = new AtomicBoolean();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        OutputStream input = process.getOutputStream();
        Input control = new Input() {
            public synchronized void write(byte[] bytes) throws IOException { input.write(bytes); input.flush(); }
            public synchronized void eof() throws IOException { input.close(); }
            public void cancel() { cancelled.set(true); }
        };
        ExecutionClock clock = new ExecutionClock(timeoutMs, properties.startupSeconds() * 1000L);
        if (marker == null) clock.start();
        Thread out = Thread.startVirtualThread(() -> {
            try {
                if (marker != null) {
                    byte[] expected = (marker + "\n").getBytes(StandardCharsets.UTF_8);
                    if (!Arrays.equals(expected, process.getInputStream().readNBytes(expected.length))) return;
                    clock.start();
                    if (stdin == null) ready.accept(control);
                }
                pump(process.getInputStream(), stdout, "stdout", count, exceeded, output);
            } catch (IOException exception) { log.debug("실행 진입 스트림이 닫혔습니다.", exception); }
        });
        Thread err = Thread.startVirtualThread(() -> pump(process.getErrorStream(), stderr, "stderr", count, exceeded, output));
        Thread writer = null;
        Verdict verdict = Verdict.FINISHED;
        try {
            if (stdin != null) writer = Thread.startVirtualThread(() -> {
                try { control.write(stdin); control.eof(); } catch (IOException ignored) { }
            });
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                if (cancelled.get()) { verdict = Verdict.CANCELLED; break; }
                if (exceeded.get()) { verdict = Verdict.OUTPUT_LIMIT; break; }
                if (clock.expired()) { verdict = Verdict.TIME_LIMIT; break; }
                if (clock.startupExpired()) { verdict = Verdict.SYSTEM_ERROR; break; }
            }
            if (verdict != Verdict.FINISHED) command(List.of("kill", name));
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            clock.stop();
            out.join(2000);
            err.join(2000);
            if (out.isAlive() || err.isAlive()) {
                command(List.of("kill", name));
                process.getInputStream().close();
                process.getErrorStream().close();
                if (verdict == Verdict.FINISHED) verdict = Verdict.RUNTIME_ERROR;
            }
            if (exceeded.get()) verdict = Verdict.OUTPUT_LIMIT;
            if (verdict == Verdict.FINISHED && process.exitValue() != 0) verdict = oom(name) ? Verdict.MEMORY_LIMIT : Verdict.RUNTIME_ERROR;
            if (verdict == Verdict.FINISHED && !clock.started()) verdict = Verdict.SYSTEM_ERROR;
            return new Result(verdict, stdout.toByteArray(), stderr.toString(StandardCharsets.UTF_8), clock.elapsedMillis());
        } finally {
            try { input.close(); } finally {
                if (writer != null) writer.interrupt();
                process.destroyForcibly();
            }
        }
    }

    private void pump(InputStream stream, ByteArrayOutputStream captured, String type, AtomicInteger count,
                      AtomicBoolean exceeded, BiConsumer<String, byte[]> output) {
        try (stream) {
            byte[] buffer = new byte[4096];
            int size;
            while ((size = stream.read(buffer)) != -1) {
                if (count.addAndGet(size) > properties.outputBytes()) { exceeded.set(true); break; }
                byte[] bytes = Arrays.copyOf(buffer, size);
                captured.writeBytes(bytes);
                output.accept(type, bytes);
            }
        } catch (IOException exception) { log.debug("실행 출력 스트림이 닫혔습니다.", exception); }
    }

    private boolean oom(String name) {
        try {
            if (command(List.of("inspect", "--format", "{{.State.OOMKilled}}", name)).strip().equals("true")) return true;
            return command(List.of("exec", name, "cat", "/sys/fs/cgroup/memory.events")).lines()
                .anyMatch(line -> line.startsWith("oom_kill ") && !line.endsWith(" 0"));
        } catch (Exception exception) { return false; }
    }

    private ProcessBuilder builder(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(properties.docker());
        command.addAll(args);
        return new ProcessBuilder(command);
    }

    private void upload(String name, String filename, String content) throws IOException, InterruptedException {
        Process process = builder(List.of("exec", "-i", name, "sh", "-c", "cat > /work/" + filename)).start();
        try {
            try (OutputStream stream = process.getOutputStream()) { stream.write(content.getBytes(StandardCharsets.UTF_8)); }
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException("소스 전달에 실패했습니다.");
        } finally { process.destroyForcibly(); }
    }

    private String command(List<String> args) throws IOException, InterruptedException {
        Process process = builder(args).start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread out = Thread.startVirtualThread(() -> copyLimited(process.getInputStream(), captured));
        Thread err = Thread.startVirtualThread(() -> copyLimited(process.getErrorStream(), captured));
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw new IOException("Docker 명령 시간이 초과되었습니다.");
            out.join(1000);
            err.join(1000);
            if (process.exitValue() != 0) throw new IOException("Docker 명령 실패: " + captured.toString(StandardCharsets.UTF_8));
            return captured.toString(StandardCharsets.UTF_8);
        } finally { process.destroyForcibly(); }
    }

    private void copyLimited(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] bytes = input.readNBytes(16384);
            synchronized (output) { output.writeBytes(bytes); }
        } catch (IOException ignored) { }
    }

    private boolean remove(String name) {
        boolean interrupted = Thread.interrupted();
        try { command(List.of("rm", "-f", name)); return true; }
        catch (Exception exception) { healthy.set(false); log.error("실행 환경 정리에 실패했습니다: {}", name, exception); return false; }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private Result cancelled() { return new Result(Verdict.CANCELLED, new byte[0], "", 0); }
    private Result systemError() { return new Result(Verdict.SYSTEM_ERROR, new byte[0], "실행 환경 오류가 발생했습니다.", 0); }
}
