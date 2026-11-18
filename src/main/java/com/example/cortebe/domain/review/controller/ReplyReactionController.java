package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.dto.CreateReplyReactionRequest;
import com.example.cortebe.domain.review.dto.ReplyReactionResponse;
import com.example.cortebe.domain.review.service.ReplyReactionService;
import com.example.cortebe.global.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/review-requests/{requestId}/replies/{replyId}/reactions")
@RequiredArgsConstructor
public class ReplyReactionController {

    private final ReplyReactionService service;

    @PostMapping
    public ResponseEntity<ReplyReactionResponse> create(@AuthenticationPrincipal Long userId,
                                                        Authentication authentication,
                                                        @PathVariable Long requestId, @PathVariable Long replyId,
                                                        @Valid @RequestBody CreateReplyReactionRequest request) {
        ReplyReactionResponse response = service.create(userId, isAdmin(authentication), requestId, replyId,
            request.type());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
            .pathSegment(response.id().toString()).build().toUri()).body(response);
    }

    @DeleteMapping("/{reactionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, Authentication authentication,
                                       @PathVariable Long requestId, @PathVariable Long replyId,
                                       @PathVariable Long reactionId) {
        service.delete(userId, isAdmin(authentication), requestId, replyId, reactionId);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + Role.ADMIN.name()));
    }
}
