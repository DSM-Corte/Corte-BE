package com.example.cortebe.domain.review.entity;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_requests",
    uniqueConstraints = @UniqueConstraint(name = "uk_review_requests_submission_id", columnNames = "submission_id"),
    indexes = @Index(name = "idx_review_requests_problem_id", columnList = "problem_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private JudgeJob submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    // 제출이 정리되거나 줄 번호가 어긋나지 않도록 요청 시점의 코드를 복사해 둔다
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String code;

    @Column(nullable = false)
    private boolean closed;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ReviewRequest(JudgeJob submission, User requester, Problem problem, Language language, String code) {
        this.submission = submission;
        this.requester = requester;
        this.problem = problem;
        this.language = language;
        this.code = code;
    }

    public boolean isRequestedBy(Long userId) {
        return requester.getId().equals(userId);
    }

    // 마지막 줄바꿈 뒤의 빈 줄은 세지 않는다
    public int lineCount() {
        int lines = code.split("\n", -1).length;
        return code.endsWith("\n") ? lines - 1 : lines;
    }

    public String replaceLine(int lineNumber, String replacement) {
        String[] lines = code.split("\n", -1);
        String suffix = lines[lineNumber - 1].endsWith("\r") ? "\r" : "";
        lines[lineNumber - 1] = replacement + suffix;
        return String.join("\n", lines);
    }
}
