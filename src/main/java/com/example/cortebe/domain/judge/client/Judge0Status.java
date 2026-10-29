package com.example.cortebe.domain.judge.client;

import com.example.cortebe.domain.judge.JudgeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

// Judge0 1.13.1 기준 status id. judgeStatus가 null이면 아직 채점 중이다
@Getter
@AllArgsConstructor
public enum Judge0Status {
    IN_QUEUE(1, null),
    PROCESSING(2, null),
    ACCEPTED(3, JudgeStatus.ACCEPTED),
    WRONG_ANSWER(4, JudgeStatus.WRONG_ANSWER),
    TIME_LIMIT_EXCEEDED(5, JudgeStatus.TIME_LIMIT_EXCEEDED),
    COMPILATION_ERROR(6, JudgeStatus.COMPILE_ERROR),
    RUNTIME_ERROR_SIGSEGV(7, JudgeStatus.RUNTIME_ERROR),
    RUNTIME_ERROR_SIGXFSZ(8, JudgeStatus.OUTPUT_LIMIT),
    RUNTIME_ERROR_SIGFPE(9, JudgeStatus.RUNTIME_ERROR),
    RUNTIME_ERROR_SIGABRT(10, JudgeStatus.RUNTIME_ERROR),
    RUNTIME_ERROR_NZEC(11, JudgeStatus.RUNTIME_ERROR),
    RUNTIME_ERROR_OTHER(12, JudgeStatus.RUNTIME_ERROR),
    INTERNAL_ERROR(13, JudgeStatus.INTERNAL_ERROR),
    EXEC_FORMAT_ERROR(14, JudgeStatus.INTERNAL_ERROR);

    private final int id;
    private final JudgeStatus judgeStatus;

    public boolean isFinished() {
        return judgeStatus != null;
    }

    // 모르는 id 하나 때문에 채점 전체가 실패하지 않도록 INTERNAL_ERROR로 본다
    public static Judge0Status from(int id) {
        return Arrays.stream(values())
            .filter(status -> status.id == id)
            .findFirst()
            .orElse(INTERNAL_ERROR);
    }
}
