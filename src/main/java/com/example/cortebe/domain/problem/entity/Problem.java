package com.example.cortebe.domain.problem.entity;

import com.example.cortebe.domain.judge.runner.Language;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
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
import java.util.EnumMap;
import java.util.Map;
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
    @CollectionTable(name = "problem_language_limits", joinColumns = @JoinColumn(name = "problem_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_problem_language_limits_problem_language", columnNames = {"problem_id", "language"}))
    @MapKeyColumn(name = "language", length = 20)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "memory_limit_mb", nullable = false)
    private Map<Language, Integer> languageMemoryLimitsMb = new EnumMap<>(Language.class);

    @ElementCollection
    @CollectionTable(name = "problem_tags", joinColumns = @JoinColumn(name = "problem_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_problem_tags_problem_tag", columnNames = {"problem_id", "tag"}),
        indexes = @Index(name = "idx_problem_tags_tag_problem", columnList = "tag,problem_id"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new LinkedHashSet<>();

    @Builder
    private Problem(String title, String description, String category, Integer memoryLimitMb,
                    Integer timeLimitMs, Set<String> tags, Map<Language, Integer> languageMemoryLimitsMb) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.memoryLimitMb = memoryLimitMb;
        this.timeLimitMs = timeLimitMs;
        if (languageMemoryLimitsMb != null) this.languageMemoryLimitsMb.putAll(languageMemoryLimitsMb);
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

    public void updateLanguageMemoryLimits(Map<Language, Integer> limits) {
        if (limits != null) {
            languageMemoryLimitsMb.clear();
            languageMemoryLimitsMb.putAll(limits);
        }
    }

    public int memoryLimitFor(Language language) {
        return languageMemoryLimitsMb.getOrDefault(language, memoryLimitMb);
    }
}
