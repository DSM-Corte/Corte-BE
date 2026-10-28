package com.example.cortebe.domain.judge.entity;

import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "judge_jobs", indexes = {
    @Index(name = "idx_judge_jobs_problem_id", columnList = "problem_id"),
    @Index(name = "idx_judge_jobs_expires_at", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgeJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;
    @Column(nullable = false)
    private boolean submission;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Language language;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Verdict verdict;
    @Lob @Column(nullable = false)
    private String code;
    @Lob @Column(nullable = false)
    private String snapshot;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private long runtimeMs;
    @Column(nullable = false)
    private int passedCount;
    @Column(nullable = false)
    private int totalCount;
    @Lob
    private String message;
    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public JudgeJob(Problem problem, User author, boolean submission, Language language, String code,
                    String snapshot, String tokenHash, int totalCount) {
        this.problem = problem;
        this.author = author;
        this.submission = submission;
        this.language = language;
        this.code = code;
        this.snapshot = snapshot;
        this.tokenHash = tokenHash;
        this.totalCount = totalCount;
        this.verdict = Verdict.QUEUED;
        this.expiresAt = author == null || !submission ? LocalDateTime.now().plusHours(1) : null;
    }

    public boolean isWrittenBy(Long userId) {
        return author != null && author.getId().equals(userId);
    }
}
