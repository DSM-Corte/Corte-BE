package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.dto.CreateLineCommentRequest;
import com.example.cortebe.domain.review.dto.LineCommentResponse;
import com.example.cortebe.domain.review.dto.UpdateLineCommentRequest;
import com.example.cortebe.domain.review.service.LineCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/review-requests/{requestId}/line-comments")
@RequiredArgsConstructor
public class LineCommentController {

    private final LineCommentService lineCommentService;

    @PostMapping
    public ResponseEntity<LineCommentResponse> create(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long requestId,
                                                      @Valid @RequestBody CreateLineCommentRequest request) {
        LineCommentResponse response = lineCommentService.create(userId, requestId, request);
        return ResponseEntity.created(URI.create("/api/review-requests/" + requestId + "/line-comments/" + response.id()))
            .body(response);
    }

    @GetMapping
    public List<LineCommentResponse> list(@AuthenticationPrincipal Long userId, @PathVariable Long requestId) {
        return lineCommentService.list(userId, requestId);
    }

    @PatchMapping("/{commentId}")
    public LineCommentResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                      @PathVariable Long commentId,
                                      @Valid @RequestBody UpdateLineCommentRequest request) {
        return lineCommentService.update(userId, requestId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                       @PathVariable Long commentId) {
        lineCommentService.delete(userId, requestId, commentId);
        return ResponseEntity.noContent().build();
    }
}
