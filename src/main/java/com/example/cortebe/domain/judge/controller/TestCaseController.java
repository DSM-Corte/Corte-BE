package com.example.cortebe.domain.judge.controller;

import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.UpdateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.TestCaseResponse;
import com.example.cortebe.domain.judge.dto.TestCasePageResponse;
import com.example.cortebe.domain.judge.service.TestCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/problems/{problemId}/test-cases")
@RequiredArgsConstructor
public class TestCaseController {
    private final TestCaseService service;

    @PostMapping
    public ResponseEntity<TestCaseResponse> create(@PathVariable Long problemId,
                                                  @Valid @RequestBody CreateTestCaseRequest request) {
        TestCaseResponse result = service.create(problemId, request);
        return ResponseEntity.created(URI.create("/api/problems/" + problemId + "/test-cases/" + result.id())).body(result);
    }

    @GetMapping
    public TestCasePageResponse list(@PathVariable Long problemId, Authentication authentication,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return service.list(problemId, admin, page, size);
    }

    @PatchMapping("/{id}")
    public TestCaseResponse update(@PathVariable Long problemId, @PathVariable Long id,
                                  @Valid @RequestBody UpdateTestCaseRequest request) {
        return service.update(problemId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long problemId, @PathVariable Long id) {
        service.delete(problemId, id);
        return ResponseEntity.noContent().build();
    }
}
