package com.example.cortebe.domain.problem.controller;

import com.example.cortebe.domain.problem.dto.CreateProblemRequest;
import com.example.cortebe.domain.problem.dto.ProblemPageResponse;
import com.example.cortebe.domain.problem.dto.ProblemResponse;
import com.example.cortebe.domain.problem.dto.UpdateProblemRequest;
import com.example.cortebe.domain.problem.service.ProblemService;
import com.example.cortebe.domain.problem.entity.DifficultyTier;
import com.example.cortebe.domain.problem.entity.SolveStatus;
import com.example.cortebe.global.error.exception.InvalidInputException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping
    public ResponseEntity<ProblemResponse> create(@Valid @RequestBody CreateProblemRequest request) {
        ProblemResponse response = problemService.create(request);
        return ResponseEntity.created(URI.create("/api/problems/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ProblemResponse get(@PathVariable Long id) {
        return problemService.get(id);
    }

    @GetMapping
    public ProblemPageResponse list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(required = false) String difficultyTier,
                                    @RequestParam(required = false) String solveStatus,
                                    @AuthenticationPrincipal Long userId) {
        return problemService.list(page, size, category, tag,
            parseFilter(difficultyTier, DifficultyTier.class, null),
            parseFilter(solveStatus, SolveStatus.class, SolveStatus.ALL), userId);
    }

    @PatchMapping("/{id}")
    public ProblemResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProblemRequest request) {
        return problemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        problemService.delete(id);
        return ResponseEntity.noContent().build();
    }
    private <E extends Enum<E>> E parseFilter(String value, Class<E> type, E defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException();
        }
    }

}
