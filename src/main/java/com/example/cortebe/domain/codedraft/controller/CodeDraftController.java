package com.example.cortebe.domain.codedraft.controller;

import com.example.cortebe.domain.codedraft.dto.CodeDraftResponse;
import com.example.cortebe.domain.codedraft.dto.SaveCodeDraftRequest;
import com.example.cortebe.domain.codedraft.service.CodeDraftService;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import com.example.cortebe.global.error.response.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/code-drafts/{problemId}/{language}")
@RequiredArgsConstructor
public class CodeDraftController {

    private final CodeDraftService codeDraftService;

    @PutMapping
    public ResponseEntity<CodeDraftResponse> save(@AuthenticationPrincipal Long userId,
                                                @Positive @PathVariable Long problemId,
                                                @PathVariable Language language,
                                                @Valid @RequestBody SaveCodeDraftRequest request) {
        CodeDraftService.SaveResult result = codeDraftService.save(userId, problemId, language, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/code-drafts/" + problemId + "/" + language)).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping
    public CodeDraftResponse get(@AuthenticationPrincipal Long userId,
                                 @Positive @PathVariable Long problemId,
                                 @PathVariable Language language) {
        return codeDraftService.get(userId, problemId, language);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableCode() {
        // JSON 파싱 예외 메시지에 코드 일부가 담길 수 있어 원인을 로그에 넣지 않는다.
        log.warn("코드 저장 요청의 JSON 형식이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ErrorResponse.of(GlobalErrorCode.INVALID_INPUT));
    }
}
