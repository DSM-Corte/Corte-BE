package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.JudgeLanguage;
import lombok.Builder;

import java.util.List;

@Builder
public record JudgeRequest(
    JudgeLanguage language,
    String code,
    List<JudgeCase> cases,
    Integer timeLimitMs,
    Integer memoryLimitMb
) {
}
