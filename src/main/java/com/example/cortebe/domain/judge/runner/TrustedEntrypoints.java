package com.example.cortebe.domain.judge.runner;

import java.util.List;

final class TrustedEntrypoints {
    private TrustedEntrypoints() { }

    static final String JAVA = """
        package com.corte.runtime;
        public final class Entry {
            public static void main(String[] args) throws Throwable {
                System.out.println(args[0]);
                System.out.flush();
                try {
                    var method = Class.forName("Main").getMethod("main", String[].class);
                    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class)
                        throw new NoSuchMethodException("public static void main(String[]) 메서드가 필요합니다.");
                    method.setAccessible(true);
                    method.invoke(null, (Object) new String[0]);
                } catch (java.lang.reflect.InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        }
        """;

    static final String CSHARP = """
        using System;
        using System.Reflection;
        using System.Threading.Tasks;
        class Bootstrap {
            static async Task Main(string[] args) {
                Console.WriteLine(args[0]);
                Console.Out.Flush();
                var entry = Assembly.LoadFrom("/work/program/Judge.dll").EntryPoint;
                object result = entry.Invoke(null, entry.GetParameters().Length == 0 ? null : new object[] { Array.Empty<string>() });
                if (result is Task task) {
                    await task;
                    if (task is Task<int> value) Environment.ExitCode = await value;
                } else if (result is int code) Environment.ExitCode = code;
            }
        }
        """;

    static final String BOOTSTRAP_PROJECT = """
        <Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType>
        <TargetFramework>net8.0</TargetFramework><EnableDefaultCompileItems>false</EnableDefaultCompileItems>
        <BaseIntermediateOutputPath>/work/bootstrap-obj/</BaseIntermediateOutputPath>
        <UseAppHost>false</UseAppHost><UseSharedCompilation>false</UseSharedCompilation></PropertyGroup>
        <ItemGroup><Compile Include="/work/Bootstrap.cs" /></ItemGroup></Project>
        """;

    static List<String> command(Language language, String marker) {
        return switch (language) {
            case C, CPP -> List.of("sh", "-c", "printf '%s\\n' \"$1\"; exec /work/program/main", "corte", marker);
            case JAVA -> List.of("java", "-XX:MaxRAMPercentage=50", "-cp", "/work/bootstrap:/work/program", "com.corte.runtime.Entry", marker);
            case CSHARP -> List.of("dotnet", "/work/bootstrap/Bootstrap.dll", marker);
            case PYTHON -> List.of("python", "-I", "-B", "-u", "-c", """
                import runpy,sys
                marker=sys.argv[1]
                sys.stdout.buffer.write((marker+'\\n').encode())
                sys.stdout.flush()
                sys.argv=['/work/program/Main.py']
                runpy.run_path('/work/program/Main.py',run_name='__main__')
                """, marker);
            case JAVASCRIPT -> List.of("node", "-e", """
                const marker=process.argv[1];
                process.stdout.write(marker+'\\n',()=>{
                    process.argv=[process.execPath,'/work/program/Main.js'];
                    require('/work/program/Main.js');
                });
                """, marker);
        };
    }
}
