package com.example.cortebe.domain.review.entity;

import com.example.cortebe.domain.user.entity.User;
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
@EntityListeners(AuditingEntityListener.class)
@Table(name = "line_comments",
    indexes = @Index(name = "idx_line_comments_review_request_id", columnList = "review_request_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_request_id", nullable = false)
    private ReviewRequest reviewRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private int lineNumber;

    @Lob
    @Column(nullable = false)
    private String content;

    @Lob
    private String suggestedCode;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public LineComment(ReviewRequest reviewRequest, User author, int lineNumber, String content, String suggestedCode) {
        this.reviewRequest = reviewRequest;
        this.author = author;
        this.lineNumber = lineNumber;
        this.content = content;
        this.suggestedCode = suggestedCode;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }

    public void update(String content, String suggestedCode) {
        if (content != null) {
            this.content = content;
        }
        if (suggestedCode != null) {
            this.suggestedCode = suggestedCode;
        }
    }
}
