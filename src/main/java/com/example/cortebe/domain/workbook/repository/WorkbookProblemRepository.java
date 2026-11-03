package com.example.cortebe.domain.workbook.repository;

import com.example.cortebe.domain.workbook.entity.WorkbookProblem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface WorkbookProblemRepository extends JpaRepository<WorkbookProblem, Long> {

    boolean existsByWorkbookIdAndProblemId(Long workbookId, Long problemId);

    long countByWorkbookId(Long workbookId);

    @Query("select wp.workbook.id as workbookId, count(wp) as problemCount from WorkbookProblem wp "
        + "where wp.workbook.id in :ids group by wp.workbook.id")
    List<CountRow> countByWorkbookIds(Collection<Long> ids);

    @Query(value = """
        select p.id as id, p.title as title from WorkbookProblem wp join wp.problem p
        where wp.workbook.id = :workbookId
        order by case p.difficulty
            when BRONZE_5 then 0 when BRONZE_4 then 1 when BRONZE_3 then 2 when BRONZE_2 then 3 when BRONZE_1 then 4
            when SILVER_5 then 5 when SILVER_4 then 6 when SILVER_3 then 7 when SILVER_2 then 8 when SILVER_1 then 9
            when GOLD_5 then 10 when GOLD_4 then 11 when GOLD_3 then 12 when GOLD_2 then 13 when GOLD_1 then 14
            when PLATINUM_5 then 15 when PLATINUM_4 then 16 when PLATINUM_3 then 17 when PLATINUM_2 then 18 when PLATINUM_1 then 19
            when DIAMOND_5 then 20 when DIAMOND_4 then 21 when DIAMOND_3 then 22 when DIAMOND_2 then 23 when DIAMOND_1 then 24
            when RUBY_5 then 25 when RUBY_4 then 26 when RUBY_3 then 27 when RUBY_2 then 28 when RUBY_1 then 29
            else 30 end, p.id
        """, countQuery = "select count(wp) from WorkbookProblem wp where wp.workbook.id = :workbookId")
    Page<Summary> findSummaries(Long workbookId, Pageable pageable);

    @Modifying
    @Query("delete from WorkbookProblem wp where wp.workbook.id = :workbookId")
    int deleteAllByWorkbookId(Long workbookId);

    @Modifying
    @Query("delete from WorkbookProblem wp where wp.problem.id = :problemId")
    int deleteAllByProblemId(Long problemId);

    @Modifying
    @Query("delete from WorkbookProblem wp where wp.workbook.id = :workbookId and wp.problem.id = :problemId")
    int deleteByWorkbookIdAndProblemId(Long workbookId, Long problemId);

    interface Summary {
        Long getId();
        String getTitle();
    }

    interface CountRow {
        Long getWorkbookId();
        long getProblemCount();
    }
}
