package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.dto.CommentPageResponse;
import com.example.cortebe.domain.review.dto.CommentResponse;
import com.example.cortebe.domain.review.dto.CreateCommentRequest;
import com.example.cortebe.domain.review.dto.UpdateCommentRequest;
import com.example.cortebe.domain.review.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/review-requests/{requestId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                                  @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.create(userId, requestId, request);
        return ResponseEntity.created(URI.create("/api/review-requests/" + requestId + "/comments/" + response.id()))
            .body(response);
    }

    @GetMapping
    public CommentPageResponse list(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return commentService.list(userId, requestId, page, size);
    }

    @PatchMapping("/{commentId}")
    public CommentResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                  @PathVariable Long commentId, @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(userId, requestId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                       @PathVariable Long commentId) {
        commentService.delete(userId, requestId, commentId);
        return ResponseEntity.noContent().build();
    }
}
