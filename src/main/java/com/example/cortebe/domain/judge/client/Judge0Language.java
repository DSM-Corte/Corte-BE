package com.example.cortebe.domain.judge.client;

import com.example.cortebe.domain.judge.JudgeLanguage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

// Judge0 1.13.1 기준 language id
@Getter
@AllArgsConstructor
public enum Judge0Language {
    C99(JudgeLanguage.C99, 50, "-std=c99"),
    CPP17(JudgeLanguage.CPP17, 54, "-std=c++17"),
    JAVA(JudgeLanguage.JAVA, 62, null),
    CSHARP(JudgeLanguage.CSHARP, 51, null),
    PYTHON(JudgeLanguage.PYTHON, 71, null),
    JAVASCRIPT(JudgeLanguage.JAVASCRIPT, 63, null),
    TEXT(JudgeLanguage.TEXT, 43, null);

    private final JudgeLanguage language;
    private final Integer id;
    private final String compilerOptions;

    public static Judge0Language from(JudgeLanguage language) {
        return Arrays.stream(values())
            .filter(judge0Language -> judge0Language.language == language)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Judge0 언어가 지정되지 않았습니다: " + language));
    }
}
