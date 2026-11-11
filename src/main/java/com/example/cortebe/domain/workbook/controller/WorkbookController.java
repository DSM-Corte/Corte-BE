package com.example.cortebe.domain.workbook.controller;

import com.example.cortebe.domain.workbook.dto.AddWorkbookProblemRequest;
import com.example.cortebe.domain.workbook.dto.CreateWorkbookRequest;
import com.example.cortebe.domain.workbook.dto.UpdateWorkbookRequest;
import com.example.cortebe.domain.workbook.dto.WorkbookDetailResponse;
import com.example.cortebe.domain.workbook.dto.WorkbookPageResponse;
import com.example.cortebe.domain.workbook.dto.WorkbookResponse;
import com.example.cortebe.domain.workbook.service.WorkbookService;
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
@RequestMapping("/api/workbooks")
@RequiredArgsConstructor
public class WorkbookController {

    private final WorkbookService workbookService;

    @PostMapping
    public ResponseEntity<WorkbookResponse> create(@Valid @RequestBody CreateWorkbookRequest request) {
        WorkbookResponse response = workbookService.create(request);
        return ResponseEntity.created(URI.create("/api/workbooks/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public WorkbookDetailResponse get(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return workbookService.get(id, page, size);
    }

    @GetMapping
    public WorkbookPageResponse list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return workbookService.list(page, size);
    }

    @PatchMapping("/{id}")
    public WorkbookResponse update(@PathVariable Long id, @Valid @RequestBody UpdateWorkbookRequest request) {
        return workbookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workbookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/problems")
    public ResponseEntity<Void> addProblem(@PathVariable Long id, @Valid @RequestBody AddWorkbookProblemRequest request) {
        workbookService.addProblem(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    public ResponseEntity<Void> removeProblem(@PathVariable Long id, @PathVariable Long problemId) {
        workbookService.removeProblem(id, problemId);
        return ResponseEntity.noContent().build();
    }
}
