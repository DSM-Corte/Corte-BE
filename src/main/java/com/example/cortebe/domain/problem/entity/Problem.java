package com.example.cortebe.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problems", indexes = @Index(name = "idx_problems_category_id", columnList = "category,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private Integer memoryLimitMb;

    @Column(nullable = false)
    private Integer timeLimitMs;

    @Builder
    private Problem(String title, String description, String category, Integer memoryLimitMb,
                    Integer timeLimitMs) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.memoryLimitMb = memoryLimitMb;
        this.timeLimitMs = timeLimitMs;
    }

    public void update(String title, String description, String category, Integer memoryLimitMb,
                       Integer timeLimitMs) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (category != null) {
            this.category = category;
        }
        if (memoryLimitMb != null) {
            this.memoryLimitMb = memoryLimitMb;
        }
        if (timeLimitMs != null) {
            this.timeLimitMs = timeLimitMs;
        }
    }
}
