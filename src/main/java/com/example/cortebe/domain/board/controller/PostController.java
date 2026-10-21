package com.example.cortebe.domain.board.controller;

import com.example.cortebe.domain.board.dto.CreatePostRequest;
import com.example.cortebe.domain.board.dto.PostPageResponse;
import com.example.cortebe.domain.board.dto.PostResponse;
import com.example.cortebe.domain.board.dto.UpdatePostRequest;
import com.example.cortebe.domain.board.service.PostService;
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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse> create(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = postService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/posts/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id) {
        return postService.get(id);
    }

    @GetMapping
    public PostPageResponse list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return postService.list(page, size);
    }

    @PatchMapping("/{id}")
    public PostResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                               @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
