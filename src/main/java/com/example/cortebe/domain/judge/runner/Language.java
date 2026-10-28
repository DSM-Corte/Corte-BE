package com.example.cortebe.domain.judge.runner;

import java.util.List;

public enum Language {
    C("gcc:14-bookworm", "Main.c", List.of("gcc", "/work/Main.c", "-O2", "-std=c17", "-o", "/work/main"), List.of("/work/main")),
    CPP("gcc:14-bookworm", "Main.cpp", List.of("g++", "/work/Main.cpp", "-O2", "-std=c++20", "-o", "/work/main"), List.of("/work/main")),
    JAVA("eclipse-temurin:21-jdk", "Main.java", List.of("javac", "-d", "/work", "/work/Main.java"), List.of("java", "-XX:MaxRAMPercentage=50", "-cp", "/work", "Main")),
    CSHARP("mcr.microsoft.com/dotnet/sdk:8.0", "Main.cs",
        List.of("sh", "-c", "mkdir -m700 /work/runtime && dotnet build /work/Judge.csproj -o /work/out --nologo --ignore-failed-sources"),
        List.of("dotnet", "/work/out/Judge.dll")),
    JAVASCRIPT("node:22-bookworm-slim", "Main.js", List.of("node", "--check", "/work/Main.js"), List.of("node", "/work/Main.js")),
    PYTHON("python:3.13-slim", "Main.py", List.of("python", "-c", "import ast; ast.parse(open('/work/Main.py').read())"), List.of("python", "-I", "-B", "-u", "/work/Main.py"));

    private final String image;
    private final String filename;
    private final List<String> compileCommand;
    private final List<String> runCommand;

    Language(String image, String filename, List<String> compileCommand, List<String> runCommand) {
        this.image = image;
        this.filename = filename;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
    }

    public String image() { return image; }
    public String filename() { return filename; }
    public List<String> compileCommand() { return compileCommand; }
    public List<String> runCommand() { return runCommand; }
}
