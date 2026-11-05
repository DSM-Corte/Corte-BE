package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.dto.CreateReplyRequest;
import com.example.cortebe.domain.review.dto.ReplyResponse;
import com.example.cortebe.domain.review.dto.UpdateReplyRequest;
import com.example.cortebe.domain.review.service.ReplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/review-requests/{requestId}")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    @PostMapping("/line-comments/{commentId}/replies")
    public ResponseEntity<ReplyResponse> createOnLineComment(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long requestId,
                                                             @PathVariable Long commentId,
                                                             @Valid @RequestBody CreateReplyRequest request) {
        return created(requestId, replyService.createOnLineComment(userId, requestId, commentId, request));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<ReplyResponse> createOnComment(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long requestId, @PathVariable Long commentId,
                                                         @Valid @RequestBody CreateReplyRequest request) {
        return created(requestId, replyService.createOnComment(userId, requestId, commentId, request));
    }

    @PatchMapping("/replies/{replyId}")
    public ReplyResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                @PathVariable Long replyId, @Valid @RequestBody UpdateReplyRequest request) {
        return replyService.update(userId, requestId, replyId, request);
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long requestId,
                                       @PathVariable Long replyId) {
        replyService.delete(userId, requestId, replyId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ReplyResponse> created(Long requestId, ReplyResponse response) {
        return ResponseEntity.created(URI.create("/api/review-requests/" + requestId + "/replies/" + response.id()))
            .body(response);
    }
}
