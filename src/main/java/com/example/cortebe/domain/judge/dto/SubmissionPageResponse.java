package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record SubmissionPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static SubmissionPageResponse from(Page<JudgeJobRepository.Summary> submissions) {
        return new SubmissionPageResponse(submissions.getContent().stream().map(Summary::from).toList(),
            submissions.getNumber(), submissions.getSize(), submissions.getTotalElements(), submissions.getTotalPages());
    }

    public record Summary(Long id, Long problemId, String problemTitle, Language language, Verdict verdict,
                          int passedCount, int totalCount, long runtimeMs, LocalDateTime createdAt) {
        public static Summary from(JudgeJobRepository.Summary submission) {
            return new Summary(submission.getId(), submission.getProblemId(), submission.getProblemTitle(),
                submission.getLanguage(), submission.getVerdict(), submission.getPassedCount(),
                submission.getTotalCount(), submission.getRuntimeMs(), submission.getCreatedAt());
        }
    }
}
