package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.UpdateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.TestCaseResponse;
import com.example.cortebe.domain.judge.dto.TestCasePageResponse;
import com.example.cortebe.domain.judge.entity.TestCase;
import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.exception.JudgeNotFoundException;
import com.example.cortebe.domain.judge.repository.TestCaseRepository;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseService {
    private final TestCaseRepository testCases;
    private final ProblemRepository problems;

    @Transactional
    public TestCaseResponse create(Long problemId, CreateTestCaseRequest request) {
        Problem problem = lockProblem(problemId);
        validateText(request.input());
        validateText(request.expectedOutput());
        if (testCases.countByProblemId(problemId) >= 100) throw new JudgeConflictException();
        return TestCaseResponse.from(testCases.saveAndFlush(TestCase.builder().problem(problem)
            .input(request.input()).expectedOutput(request.expectedOutput())
            .publicExample(request.publicExample()).position(request.position()).build()));
    }

    public TestCasePageResponse list(Long problemId, boolean admin, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidInputException();
        if (!problems.existsById(problemId)) throw new JudgeNotFoundException();
        Page<TestCaseRepository.Summary> result = testCases.findVisible(problemId, admin,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new TestCasePageResponse(result.map(TestCaseResponse::from).getContent(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TestCaseResponse update(Long problemId, Long id, UpdateTestCaseRequest request) {
        lockProblem(problemId);
        validateText(request.input());
        validateText(request.expectedOutput());
        TestCase value = find(problemId, id);
        value.update(request.input(), request.expectedOutput(), request.publicExample(), request.position());
        testCases.flush();
        return TestCaseResponse.from(value);
    }

    @Transactional
    public void delete(Long problemId, Long id) {
        lockProblem(problemId);
        testCases.delete(find(problemId, id));
    }

    private Problem lockProblem(Long id) {
        return problems.findLockedById(id).orElseThrow(JudgeNotFoundException::new);
    }

    private TestCase find(Long problemId, Long id) {
        return testCases.findByIdAndProblemId(id, problemId).orElseThrow(JudgeNotFoundException::new);
    }

    private void validateText(String text) {
        if (text != null && text.getBytes(StandardCharsets.UTF_8).length > 1048576) throw new InvalidInputException();
    }
}
