package com.example.cortebe.domain.judge.entity;

import com.example.cortebe.domain.problem.entity.Problem;
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
@Table(name = "test_cases", indexes = @Index(name = "idx_test_cases_problem_id", columnList = "problem_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;
    @Lob @Column(nullable = false)
    private String input;
    @Lob @Column(nullable = false)
    private String expectedOutput;
    @Column(nullable = false)
    private boolean publicExample;
    @Column(nullable = false)
    private int position;
    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TestCase(Problem problem, String input, String expectedOutput, boolean publicExample, int position) {
        this.problem = problem;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.publicExample = publicExample;
        this.position = position;
    }

    public void update(String input, String expectedOutput, Boolean publicExample, Integer position) {
        if (input != null) this.input = input;
        if (expectedOutput != null) this.expectedOutput = expectedOutput;
        if (publicExample != null) this.publicExample = publicExample;
        if (position != null) this.position = position;
    }
}
