package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.JudgeStatus;
import lombok.Builder;

import java.util.List;

// timeMs·memoryKb는 측정되지 않으면 null이다
@Builder
public record JudgeResult(
    JudgeStatus status,
    Integer timeMs,
    Long memoryKb,
    List<CaseResult> cases
) {

    public record CaseResult(
        JudgeStatus status,
        Integer timeMs,
        Long memoryKb
    ) {
    }
}
