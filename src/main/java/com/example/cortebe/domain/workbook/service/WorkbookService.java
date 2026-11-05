package com.example.cortebe.domain.workbook.service;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.exception.ProblemNotFoundException;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.workbook.dto.AddWorkbookProblemRequest;
import com.example.cortebe.domain.workbook.dto.CreateWorkbookRequest;
import com.example.cortebe.domain.workbook.dto.UpdateWorkbookRequest;
import com.example.cortebe.domain.workbook.dto.WorkbookDetailResponse;
import com.example.cortebe.domain.workbook.dto.WorkbookPageResponse;
import com.example.cortebe.domain.workbook.dto.WorkbookResponse;
import com.example.cortebe.domain.workbook.entity.Workbook;
import com.example.cortebe.domain.workbook.entity.WorkbookProblem;
import com.example.cortebe.domain.workbook.exception.DuplicateWorkbookProblemException;
import com.example.cortebe.domain.workbook.exception.WorkbookNotFoundException;
import com.example.cortebe.domain.workbook.exception.WorkbookProblemNotFoundException;
import com.example.cortebe.domain.workbook.repository.WorkbookProblemRepository;
import com.example.cortebe.domain.workbook.repository.WorkbookRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkbookService {

    private static final String PROBLEM_UNIQUE_CONSTRAINT = "uk_workbook_problems_workbook_problem";

    private final WorkbookRepository workbooks;
    private final WorkbookProblemRepository workbookProblems;
    private final ProblemRepository problems;

    @Transactional
    public WorkbookResponse create(CreateWorkbookRequest request) {
        Workbook workbook = workbooks.save(Workbook.builder().name(normalizeName(request.name())).build());
        return WorkbookResponse.from(workbook, 0);
    }

    public WorkbookDetailResponse get(Long id, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        Workbook workbook = workbooks.findById(id).orElseThrow(WorkbookNotFoundException::new);
        return WorkbookDetailResponse.from(workbook, workbookProblems.findSummaries(id, pageable));
    }

    public WorkbookPageResponse list(int page, int size) {
        var result = workbooks.findSummaries(pageRequest(page, size).withSort(Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Long> counts = new HashMap<>();
        if (!result.isEmpty()) {
            workbookProblems.countByWorkbookIds(result.stream().map(WorkbookRepository.Summary::getId).toList())
                .forEach(row -> counts.put(row.getWorkbookId(), row.getProblemCount()));
        }
        return WorkbookPageResponse.from(result, counts);
    }

    @Transactional
    public WorkbookResponse update(Long id, UpdateWorkbookRequest request) {
        Workbook workbook = lockedWorkbook(id);
        workbook.update(request.name() == null ? null : normalizeName(request.name()));
        return WorkbookResponse.from(workbook, workbookProblems.countByWorkbookId(id));
    }

    @Transactional
    public void delete(Long id) {
        Workbook workbook = lockedWorkbook(id);
        workbookProblems.deleteAllByWorkbookId(id);
        workbooks.delete(workbook);
    }

    @Transactional
    public void addProblem(Long id, AddWorkbookProblemRequest request) {
        // 원본 문제 삭제와 동일한 순서로 잠가 삭제 직후 연결이 생기는 것을 막는다.
        Problem problem = problems.findLockedById(request.problemId()).orElseThrow(ProblemNotFoundException::new);
        Workbook workbook = lockedWorkbook(id);
        if (workbookProblems.existsByWorkbookIdAndProblemId(id, request.problemId())) {
            throw new DuplicateWorkbookProblemException();
        }
        try {
            workbookProblems.saveAndFlush(WorkbookProblem.builder().workbook(workbook).problem(problem).build());
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateProblem(exception)) throw new DuplicateWorkbookProblemException();
            throw exception;
        }
    }

    @Transactional
    public void removeProblem(Long id, Long problemId) {
        if (problemId <= 0) throw new InvalidInputException();
        // 이미 삭제된 원본 문제도 포함되지 않은 문제(W003)로 처리한다.
        problems.findLockedById(problemId);
        lockedWorkbook(id);
        if (workbookProblems.deleteByWorkbookIdAndProblemId(id, problemId) == 0) {
            throw new WorkbookProblemNotFoundException();
        }
    }

    private Workbook lockedWorkbook(Long id) {
        return workbooks.findLockedById(id).orElseThrow(WorkbookNotFoundException::new);
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidInputException();
        }
        return PageRequest.of(page, size);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new InvalidInputException();
        String normalized = name.strip();
        if (normalized.length() > 100) throw new InvalidInputException();
        return normalized;
    }

    private boolean isDuplicateProblem(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                String name = violation.getConstraintName();
                if (name == null) return false;
                name = name.split(" ", 2)[0];
                name = name.substring(name.lastIndexOf('.') + 1);
                return PROBLEM_UNIQUE_CONSTRAINT.equalsIgnoreCase(name);
            }
        }
        return false;
    }
}
