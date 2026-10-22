package com.example.cortebe.domain.problem.repository;

import com.example.cortebe.domain.problem.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    Page<Summary> findAllBy(Pageable pageable);
    Page<Summary> findAllByCategory(String category, Pageable pageable);

    @Query(value = """
        select p.id as id, p.title as title, p.category as category
        from Problem p
        where (:category is null or p.category = :category) and :tag member of p.tags
        """, countQuery = """
        select count(p) from Problem p
        where (:category is null or p.category = :category) and :tag member of p.tags
        """)
    Page<Summary> findAllByTag(@Param("category") String category, @Param("tag") String tag, Pageable pageable);

    @Query("""
        select p.id as problemId, tag as tag
        from Problem p join p.tags tag
        where p.id in :problemIds
        order by p.id, tag
        """)
    List<TagRow> findTagsByProblemIds(@Param("problemIds") Collection<Long> problemIds);

    interface Summary {
        Long getId();
        String getTitle();
        String getCategory();
    }

    interface TagRow {
        Long getProblemId();
        String getTag();
    }
}
