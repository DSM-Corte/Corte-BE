package com.example.cortebe.domain.judge.runner;

import java.util.List;

public enum Language {
    C("gcc:14-bookworm", "Main.c", List.of("gcc", "/work/Main.c", "-O2", "-std=c17", "-o", "/work/program/main")),
    CPP("gcc:14-bookworm", "Main.cpp", List.of("g++", "/work/Main.cpp", "-O2", "-std=c++20", "-o", "/work/program/main")),
    JAVA("eclipse-temurin:21-jdk", "Main.java", List.of("javac", "-d", "/work/program", "/work/Main.java")),
    CSHARP("mcr.microsoft.com/dotnet/sdk:8.0", "Main.cs",
        List.of("dotnet", "build", "/work/Judge.csproj", "-o", "/work/program", "--nologo", "--ignore-failed-sources")),
    JAVASCRIPT("node:22-bookworm-slim", "Main.js", List.of("node", "--check", "/work/Main.js")),
    PYTHON("python:3.13-slim", "Main.py", List.of("python", "-c", "import ast; ast.parse(open('/work/Main.py').read())"));

    private final String image;
    private final String filename;
    private final List<String> compileCommand;

    Language(String image, String filename, List<String> compileCommand) {
        this.image = image;
        this.filename = filename;
        this.compileCommand = compileCommand;
    }

    public String image() { return image; }
    public String filename() { return filename; }
    public List<String> compileCommand() { return compileCommand; }
}
