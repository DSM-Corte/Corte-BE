package com.example.cortebe.domain.board.repository;

import com.example.cortebe.domain.board.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<PostComment> findWithAuthorById(Long id);

    // 작성자는 join으로 한 번에 가져온다 (N+1 방지)
    @Query(value = "select c.id as id, c.content as content, u.nickname as authorNickname, "
        + "c.createdAt as createdAt, c.updatedAt as updatedAt "
        + "from PostComment c join c.author u where c.post.id = :postId",
        countQuery = "select count(c) from PostComment c where c.post.id = :postId")
    Page<Summary> findSummaries(@Param("postId") Long postId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PostComment c where c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    interface Summary {
        Long getId();
        String getContent();
        String getAuthorNickname();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }
}
