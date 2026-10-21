package com.example.cortebe.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

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

    @ElementCollection
    @CollectionTable(name = "problem_tags", joinColumns = @JoinColumn(name = "problem_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_problem_tags_problem_tag", columnNames = {"problem_id", "tag"}),
        indexes = @Index(name = "idx_problem_tags_tag_problem", columnList = "tag,problem_id"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new LinkedHashSet<>();

    @Builder
    private Problem(String title, String description, String category, Integer memoryLimitMb,
                    Integer timeLimitMs, Set<String> tags) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.memoryLimitMb = memoryLimitMb;
        this.timeLimitMs = timeLimitMs;
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public void update(String title, String description, String category, Integer memoryLimitMb,
                       Integer timeLimitMs, Set<String> tags) {
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
        if (tags != null) {
            this.tags.clear();
            this.tags.addAll(tags);
        }
    }
}
