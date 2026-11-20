package com.example.cortebe.domain.aireview.service;

import com.example.cortebe.domain.aireview.dto.AiReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 외부 호출 동안 DB 연결을 붙들지 않도록 이 클래스에는 트랜잭션을 두지 않는다
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final AiReviewStore store;
    private final ClaudeReviewer reviewer;

    public AiReviewResponse create(Long userId, Long submissionId) {
        ClaudeReviewer.Source source = store.loadSource(userId, submissionId);
        ClaudeReviewer.Result result = reviewer.review(source);
        return store.save(userId, submissionId, result);
    }

    public AiReviewResponse get(Long userId, Long submissionId) {
        return store.get(userId, submissionId);
    }
}
