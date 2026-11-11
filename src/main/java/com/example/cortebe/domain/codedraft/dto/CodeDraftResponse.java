package com.example.cortebe.domain.codedraft.dto;

import com.example.cortebe.domain.codedraft.entity.CodeDraft;
import com.example.cortebe.domain.judge.runner.Language;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CodeDraftResponse(Long problemId, Language language, String code, LocalDateTime updatedAt) {

    public static CodeDraftResponse from(CodeDraft draft) {
        return CodeDraftResponse.builder().problemId(draft.getProblem().getId()).language(draft.getLanguage())
            .code(draft.getCode()).updatedAt(draft.getUpdatedAt()).build();
    }

    public static CodeDraftResponse empty(Long problemId, Language language) {
        return CodeDraftResponse.builder().problemId(problemId).language(language).code("").updatedAt(null).build();
    }
}
