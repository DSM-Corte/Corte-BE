package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Verdict;
import lombok.Builder;

@Builder
public record JobResponse(Long id, Verdict verdict, int passedCount, int totalCount, long runtimeMs,
                          String message, String accessToken, String streamPath, String stdout, String stderr) {
    public static JobResponse from(JudgeJob job) {
        return JobResponse.builder().id(job.getId()).verdict(job.getVerdict()).passedCount(job.getPassedCount())
            .totalCount(job.getTotalCount()).runtimeMs(job.getRuntimeMs()).message(job.getMessage())
            .stdout(job.getStdout()).stderr(job.getStderr()).build();
    }
}
