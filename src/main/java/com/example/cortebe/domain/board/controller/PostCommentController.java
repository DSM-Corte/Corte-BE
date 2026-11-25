package com.example.cortebe.domain.board.controller;

import com.example.cortebe.domain.board.dto.CreatePostCommentRequest;
import com.example.cortebe.domain.board.dto.PostCommentPageResponse;
import com.example.cortebe.domain.board.dto.PostCommentResponse;
import com.example.cortebe.domain.board.dto.UpdatePostCommentRequest;
import com.example.cortebe.domain.board.service.PostCommentService;
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
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService postCommentService;

    @PostMapping
    public ResponseEntity<PostCommentResponse> create(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long postId,
                                                      @Valid @RequestBody CreatePostCommentRequest request) {
        PostCommentResponse response = postCommentService.create(userId, postId, request);
        return ResponseEntity.created(URI.create("/api/posts/" + postId + "/comments/" + response.id()))
            .body(response);
    }

    @GetMapping
    public PostCommentPageResponse list(@PathVariable Long postId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return postCommentService.list(postId, page, size);
    }

    @PatchMapping("/{commentId}")
    public PostCommentResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long postId,
                                      @PathVariable Long commentId,
                                      @Valid @RequestBody UpdatePostCommentRequest request) {
        return postCommentService.update(userId, postId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long postId,
                                       @PathVariable Long commentId) {
        postCommentService.delete(userId, postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
