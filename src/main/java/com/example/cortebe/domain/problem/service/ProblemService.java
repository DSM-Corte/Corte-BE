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
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problemRepository;

    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        Problem problem = problemRepository.save(new Problem(request.title(), request.description(),
            request.inputFormat(), request.outputFormat(), request.exampleInput(), request.exampleOutput()));
        return toResponse(problem);
    }

    public ProblemResponse get(Long id) {
        return toResponse(findProblem(id));
    }

    public ProblemPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        }
        Page<ProblemRepository.Summary> problems = problemRepository.findAllBy(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new ProblemPageResponse(problems.getContent().stream()
            .map(problem -> new ProblemPageResponse.Summary(problem.getId(), problem.getTitle())).toList(),
            problems.getNumber(), problems.getSize(), problems.getTotalElements(), problems.getTotalPages());
    }

    @Transactional
    public ProblemResponse update(Long id, UpdateProblemRequest request) {
        Problem problem = findProblem(id);
        problem.update(request.title(), request.description(), request.inputFormat(), request.outputFormat(),
            request.exampleInput(), request.exampleOutput());
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
        return new ProblemResponse(problem.getId(), problem.getTitle(), problem.getDescription(),
            problem.getInputFormat(), problem.getOutputFormat(), problem.getExampleInput(),
            problem.getExampleOutput());
    }
}
