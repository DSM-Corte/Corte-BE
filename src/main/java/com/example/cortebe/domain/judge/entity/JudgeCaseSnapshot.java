package com.example.cortebe.domain.judge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "judge_case_snapshots", indexes = @Index(name = "idx_judge_case_snapshots_job_order", columnList = "job_id,case_order"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgeCaseSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JudgeJob job;
    @Column(name = "case_order", nullable = false)
    private int caseOrder;
    @Lob @Column(nullable = false)
    private String input;
    @Lob @Column(nullable = false)
    private String expectedOutput;
    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private JudgeCaseSnapshot(JudgeJob job, int caseOrder, String input, String expectedOutput) {
        this.job = job;
        this.caseOrder = caseOrder;
        this.input = input;
        this.expectedOutput = expectedOutput;
    }
}
