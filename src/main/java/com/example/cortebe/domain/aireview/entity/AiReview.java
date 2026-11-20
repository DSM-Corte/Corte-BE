package com.example.cortebe.domain.aireview.entity;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "ai_reviews", uniqueConstraints = {
    @UniqueConstraint(name = AiReview.SUBMISSION_UNIQUE_CONSTRAINT, columnNames = "submission_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReview {

    public static final String SUBMISSION_UNIQUE_CONSTRAINT = "uk_ai_reviews_submission_id";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private JudgeJob submission;

    @Column(nullable = false, length = 50)
    private String model;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String summary;

    // 줄 코멘트 배열과 개선 제안은 조회 조건이 없어 JSON 문자열로 저장한다
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String comments;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String suggestion;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AiReview(JudgeJob submission, String model, String summary, String comments, String suggestion) {
        this.submission = submission;
        this.model = model;
        this.summary = summary;
        this.comments = comments;
        this.suggestion = suggestion;
    }
}
