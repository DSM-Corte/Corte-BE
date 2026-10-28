package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.entity.TestCase;
import com.example.cortebe.domain.judge.repository.TestCaseRepository;
import lombok.Builder;

@Builder
public record TestCaseResponse(Long id, String input, String expectedOutput, boolean publicExample, int position) {
    public static TestCaseResponse from(TestCase value) {
        return TestCaseResponse.builder().id(value.getId()).input(value.getInput())
            .expectedOutput(value.getExpectedOutput()).publicExample(value.isPublicExample())
            .position(value.getPosition()).build();
    }

    public static TestCaseResponse from(TestCaseRepository.Summary value) {
        return TestCaseResponse.builder().id(value.getId()).input(value.getInput())
            .expectedOutput(value.getExpectedOutput()).publicExample(value.getPublicExample())
            .position(value.getPosition()).build();
    }
}
