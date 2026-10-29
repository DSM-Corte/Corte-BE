package com.example.cortebe.domain.judge.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class Judge0Dto {

    private Judge0Dto() {
    }

    // cpu_time_limit은 초, memory_limit은 KB
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubmissionRequest(
        @JsonProperty("source_code") String sourceCode,
        @JsonProperty("language_id") Integer languageId,
        @JsonProperty("compiler_options") String compilerOptions,
        String stdin,
        @JsonProperty("expected_output") String expectedOutput,
        @JsonProperty("cpu_time_limit") Double cpuTimeLimit,
        @JsonProperty("memory_limit") Long memoryLimit,
        @JsonProperty("enable_network") Boolean enableNetwork,
        @JsonProperty("wall_time_limit") Double wallTimeLimit,
        @JsonProperty("max_file_size") Integer maxFileSize
    ) {
    }

    public record BatchRequest(
        List<SubmissionRequest> submissions
    ) {
    }

    // 형식이 잘못된 제출은 token 대신 오류 필드가 온다
    public record TokenResponse(
        String token
    ) {
    }

    public record StatusResponse(
        Integer id,
        String description
    ) {
    }

    // time은 "0.001" 같은 초 단위 문자열, memory는 KB
    public record SubmissionResponse(
        String token,
        StatusResponse status,
        String time,
        Long memory,
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compileOutput
    ) {
    }

    public record BatchResponse(
        List<SubmissionResponse> submissions
    ) {
    }
}
