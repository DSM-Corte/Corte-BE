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

// 부모는 줄 코멘트 또는 전체 댓글 중 하나만 채운다
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_replies", indexes = {
    @Index(name = "idx_review_replies_line_comment_id", columnList = "line_comment_id"),
    @Index(name = "idx_review_replies_review_comment_id", columnList = "review_comment_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_request_id", nullable = false)
    private ReviewRequest reviewRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_comment_id")
    private LineComment lineComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_comment_id")
    private ReviewComment reviewComment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ReviewReply(ReviewRequest reviewRequest, LineComment lineComment, ReviewComment reviewComment,
                       User author, String content) {
        this.reviewRequest = reviewRequest;
        this.lineComment = lineComment;
        this.reviewComment = reviewComment;
        this.author = author;
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }

    public void update(String content) {
        if (content != null) {
            this.content = content;
        }
    }
}
