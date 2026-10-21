package com.example.cortebe.domain.problem.service;

import com.example.cortebe.domain.problem.dto.CreateProblemRequest;
import com.example.cortebe.domain.problem.dto.ProblemPageResponse;
import com.example.cortebe.domain.problem.dto.ProblemResponse;
import com.example.cortebe.domain.problem.dto.UpdateProblemRequest;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;

    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        Problem problem = problemRepository.save(Problem.builder()
            .title(request.title())
            .description(request.description())
            .category(request.category())
            .memoryLimitMb(request.memoryLimitMb())
            .timeLimitMs(request.timeLimitMs())
            .build());
        return toResponse(problem);
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(Long id) {
        return toResponse(findProblem(id));
    }

    @Transactional(readOnly = true)
    public ProblemPageResponse list(int page, int size, String category) {
        if (page < 0 || size < 1 || size > 100
            || (category != null && (category.isBlank() || category.length() > 50))) {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<ProblemRepository.Summary> problems = category == null
            ? problemRepository.findAllBy(pageRequest)
            : problemRepository.findAllByCategory(category, pageRequest);
        return ProblemPageResponse.builder()
            .content(problems.getContent().stream()
                .map(problem -> ProblemPageResponse.Summary.builder()
                    .id(problem.getId())
                    .title(problem.getTitle())
                    .category(problem.getCategory())
                    .build()).toList())
            .page(problems.getNumber())
            .size(problems.getSize())
            .totalElements(problems.getTotalElements())
            .totalPages(problems.getTotalPages())
            .build();
    }

    @Transactional
    public ProblemResponse update(Long id, UpdateProblemRequest request) {
        Problem problem = findProblem(id);
        problem.update(request.title(), request.description(), request.category(), request.memoryLimitMb(),
            request.timeLimitMs());
        return toResponse(problem);
    }

    @Transactional
    public void delete(Long id) {
        problemRepository.delete(findProblem(id));
    }

    private Problem findProblem(Long id) {
        return problemRepository.findById(id)
            .orElseThrow(() -> new CorteException(GlobalErrorCode.RESOURCE_NOT_FOUND));
    }

    private ProblemResponse toResponse(Problem problem) {
        return ProblemResponse.builder()
            .id(problem.getId())
            .title(problem.getTitle())
            .description(problem.getDescription())
            .category(problem.getCategory())
            .memoryLimitMb(problem.getMemoryLimitMb())
            .timeLimitMs(problem.getTimeLimitMs())
            .build();
    }
}
