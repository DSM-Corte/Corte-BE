package com.example.cortebe.domain.codedraft.entity;

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
@Table(name = "code_drafts",
    uniqueConstraints = @UniqueConstraint(name = "uk_code_drafts_user_problem_language", columnNames = {"user_id", "problem_id", "language"}),
    indexes = @Index(name = "idx_code_drafts_problem_id", columnList = "problem_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String code;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CodeDraft(User user, Problem problem, Language language, String code) {
        this.user = user;
        this.problem = problem;
        this.language = language;
        this.code = code;
    }

    public void update(String code) {
        this.code = code;
    }
}
