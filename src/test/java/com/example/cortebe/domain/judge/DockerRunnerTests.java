package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.judge.runner.DockerRunner;
import com.example.cortebe.domain.judge.runner.JudgeProperties;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JUDGE_DOCKER_TESTS", matches = "true")
class DockerRunnerTests {
    private final DockerRunner runner = new DockerRunner(new JudgeProperties(true,
        System.getenv().getOrDefault("JUDGE_DOCKER", "docker"), 2, 16, 30, 120, 60, 1048576));

    @Test
    void 한_번_준비한_코드로_여러_케이스를_실행하고_파일은_격리한다() throws Exception {
        // given
        String code = "import os\nprint('dirty' if os.path.exists('/work/changed') else 'clean')\nopen('/work/changed','w').write('x')";
        try (var program = runner.prepare(Language.PYTHON, code, new AtomicBoolean())) {
            assertThat(program.compilation().verdict()).isEqualTo(Verdict.FINISHED);
            for (int i = 0; i < 2; i++) {
                // when
                var result = program.execute(256, 10000, new byte[0], ignored -> { }, (type, bytes) -> { }, new AtomicBoolean());
                // then
                assertThat(result.verdict()).as(result.stderr()).isEqualTo(Verdict.FINISHED);
                assertThat(new String(result.stdout(), StandardCharsets.UTF_8)).isEqualTo("clean\n");
            }
        }
    }

    @Test
    void 여섯_언어가_격리된_환경에서_표준입출력을_처리한다() {
        // given
        Map<Language, String> programs = Map.of(
            Language.C, "#include <stdio.h>\nint main(){int a,b;scanf(\"%d%d\",&a,&b);printf(\"%d\\n\",a+b);}",
            Language.CPP, "#include <iostream>\nint main(){int a,b;std::cin>>a>>b;std::cout<<a+b<<'\\n';}",
            Language.JAVA, "import java.util.*;class Main{public static void main(String[] args){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}",
            Language.CSHARP, "using System;class MainClass{static void Main(){var a=Console.ReadLine().Split();Console.WriteLine(int.Parse(a[0])+int.Parse(a[1]));}}",
            Language.JAVASCRIPT, "const a=require('fs').readFileSync(0,'utf8').trim().split(/\\s+/).map(Number);console.log(a[0]+a[1]);",
            Language.PYTHON, "a,b=map(int,input().split());print(a+b)");
        // when & then
        programs.forEach((language, source) -> {
            DockerRunner.Result result = execute(language, source, 10000);
            assertThat(result.verdict()).as("%s: %s", language, result.stderr()).isEqualTo(Verdict.FINISHED);
            assertThat(new String(result.stdout(), StandardCharsets.UTF_8)).isEqualTo("3\n");
        });
    }

    @Test
    void 무한루프와_대량출력을_제한한다() {
        // when & then
        assertThat(execute(Language.PYTHON, "while True: pass", 200).verdict()).isEqualTo(Verdict.TIME_LIMIT);
        assertThat(execute(Language.PYTHON, "print('x'*2000000)", 10000).verdict()).isEqualTo(Verdict.OUTPUT_LIMIT);
    }

    @Test
    void 루트_쓰기와_외부_네트워크가_차단되고_서비스_비밀값이_없다() {
        // given
        String source = """
            import os,socket
            assert os.getuid() != 0
            assert 'JWT_SECRET' not in os.environ
            assert 'DB_PASSWORD' not in os.environ
            assert not os.path.exists('/var/run/docker.sock')
            try:
                open('/host-write-check','w').write('x')
                raise AssertionError('root writable')
            except OSError:
                pass
            s=socket.socket()
            s.settimeout(0.5)
            try:
                s.connect(('1.1.1.1',443))
                raise AssertionError('network accessible')
            except OSError:
                pass
            print('isolated')
            """;
        // when
        DockerRunner.Result result = execute(Language.PYTHON, source, 10000);
        // then
        assertThat(result.verdict()).as(result.stderr()).isEqualTo(Verdict.FINISHED);
        assertThat(new String(result.stdout(), StandardCharsets.UTF_8)).isEqualTo("isolated\n");
    }

    @Test
    void 메모리_제한을_넘으면_메모리초과로_판정한다() {
        // given
        String source = "a=bytearray(1024*1024*1024);print(len(a))";
        // when & then
        assertThat(execute(Language.PYTHON, source, 10000).verdict()).isEqualTo(Verdict.MEMORY_LIMIT);
    }

    private DockerRunner.Result execute(Language language, String source, long timeout) {
        return runner.execute(language, source, 256, timeout, "1 2\n".getBytes(StandardCharsets.UTF_8),
            ignored -> { }, (type, bytes) -> { }, new AtomicBoolean());
    }
}
