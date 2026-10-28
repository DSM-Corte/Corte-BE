package com.example.cortebe.domain.problem.service;

import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.TestCaseRepository;
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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final JudgeJobRepository judgeJobs;
    private final TestCaseRepository testCases;

    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        Problem problem = problemRepository.save(Problem.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .category(request.getCategory())
            .memoryLimitMb(request.getMemoryLimitMb())
            .timeLimitMs(request.getTimeLimitMs())
            .tags(normalizeTags(request.getTags()))
            .build());
        return toResponse(problem);
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(Long id) {
        return toResponse(findProblem(id));
    }

    @Transactional(readOnly = true)
    public ProblemPageResponse list(int page, int size, String category, String tag) {
        if (page < 0 || size < 1 || size > 100
            || (category != null && (category.isBlank() || category.length() > 50))) {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        }
        if (tag != null && (tag.isBlank() || tag.length() > 30)) {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<ProblemRepository.Summary> problems;
        if (tag != null) {
            problems = problemRepository.findAllByTag(category, tag.strip(), pageRequest);
        } else {
            problems = category == null
                ? problemRepository.findAllBy(pageRequest)
                : problemRepository.findAllByCategory(category, pageRequest);
        }
        Map<Long, Set<String>> tagsByProblem = new HashMap<>();
        if (!problems.isEmpty()) {
            problemRepository.findTagsByProblemIds(problems.getContent().stream().map(ProblemRepository.Summary::getId).toList())
                .forEach(row -> tagsByProblem.computeIfAbsent(row.getProblemId(), id -> new LinkedHashSet<>()).add(row.getTag()));
        }
        return ProblemPageResponse.builder()
            .content(problems.getContent().stream()
                .map(problem -> ProblemPageResponse.Summary.builder()
                    .id(problem.getId())
                    .title(problem.getTitle())
                    .category(problem.getCategory())
                    .tags(tagsByProblem.getOrDefault(problem.getId(), Set.of()))
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
        problem.update(request.getTitle(), request.getDescription(), request.getCategory(), request.getMemoryLimitMb(),
            request.getTimeLimitMs(), normalizeTags(request.getTags()));
        return toResponse(problem);
    }

    @Transactional
    public void delete(Long id) {
        Problem problem = problemRepository.findLockedById(id)
            .orElseThrow(() -> new CorteException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        if (judgeJobs.existsByProblemIdAndSubmissionTrue(id)) throw new JudgeConflictException();
        judgeJobs.deleteExecutions(id);
        testCases.deleteAllByProblemId(id);
        problemRepository.delete(problem);
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
            .tags(new LinkedHashSet<>(problem.getTags()))
            .build();
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        tags.forEach(tag -> normalized.add(tag.strip()));
        return normalized;
    }
}
