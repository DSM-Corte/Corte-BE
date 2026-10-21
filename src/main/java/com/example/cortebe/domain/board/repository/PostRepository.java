package com.example.cortebe.domain.board.repository;

import com.example.cortebe.domain.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<Post> findWithAuthorById(Long id);

    // 본문 없이 목록에 필요한 컬럼만 조회하고, 작성자는 join으로 한 번에 가져온다 (N+1 방지)
    @Query(value = "select p.id as id, p.title as title, u.nickname as authorNickname, p.createdAt as createdAt "
        + "from Post p join p.author u",
        countQuery = "select count(p) from Post p")
    Page<Summary> findSummaries(Pageable pageable);

    interface Summary {
        Long getId();
        String getTitle();
        String getAuthorNickname();
        LocalDateTime getCreatedAt();
    }
}
