package com.example.cortebe.domain.judge.dto;

import java.util.List;

public record TestCasePageResponse(List<TestCaseResponse> content, int page, int size, long totalElements, int totalPages) {
}
