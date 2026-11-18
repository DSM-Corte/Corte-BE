package com.example.cortebe.domain.review.entity;

import com.example.cortebe.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_reply_reactions", uniqueConstraints = @UniqueConstraint(
    name = ReviewReplyReaction.UNIQUE_REACTION, columnNames = {"reply_id", "author_id", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReplyReaction {

    public static final String UNIQUE_REACTION = "uk_review_reply_reactions_reply_id_author_id_type";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부모 댓글을 삭제할 때 답글을 벌크 삭제하므로 DB에서 반응까지 정리한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reply_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ReviewReply reply;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // 반응 종류를 늘려도 기존 DB의 값 제약을 바꿀 필요가 없도록 문자열로 저장한다.
    @Column(name = "type", nullable = false, length = 20)
    private String typeCode;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ReviewReplyReaction(ReviewReply reply, User author, ReplyReactionType type) {
        this.reply = reply;
        this.author = author;
        this.typeCode = type.name();
    }

    public ReplyReactionType getType() {
        return ReplyReactionType.valueOf(typeCode);
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }
}
