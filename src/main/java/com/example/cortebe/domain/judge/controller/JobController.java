package com.example.cortebe.domain.judge.controller;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.service.JobManager;
import com.example.cortebe.domain.judge.service.JobStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class JobController {
    private final JobManager manager;
    private final JobStore store;

    @PostMapping("/api/submissions")
    public ResponseEntity<JobResponse> submit(@AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateJobRequest body, HttpServletRequest request) {
        JobResponse result = manager.create(userId, request.getRemoteAddr(), body, true);
        return ResponseEntity.created(URI.create("/api/submissions/" + result.id())).body(result);
    }

    @GetMapping("/api/submissions/{id}")
    public JobResponse get(@PathVariable Long id, @AuthenticationPrincipal Long userId,
                          @RequestHeader(value = "X-Judge-Token", required = false) String token) {
        return store.get(id, userId, token, true);
    }

    @PostMapping("/api/executions")
    public ResponseEntity<JobResponse> execute(@AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateJobRequest body, HttpServletRequest request) {
        JobResponse result = manager.create(userId, request.getRemoteAddr(), body, false);
        return ResponseEntity.created(URI.create("/api/executions/" + result.id())).body(result);
    }

    @GetMapping("/api/executions/{id}")
    public JobResponse getExecution(@PathVariable Long id, @AuthenticationPrincipal Long userId,
        @RequestHeader(value = "X-Judge-Token", required = false) String token) {
        return store.get(id, userId, token, false);
    }
}
