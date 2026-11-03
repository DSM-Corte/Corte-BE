package com.example.cortebe.domain.workbook.repository;

import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.workbook.entity.Workbook;
import com.example.cortebe.domain.workbook.entity.WorkbookProblem;
import com.example.cortebe.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class WorkbookProblemRepositoryTests {

    @Autowired private WorkbookRepository workbooks;
    @Autowired private WorkbookProblemRepository links;
    @Autowired private ProblemRepository problems;
    @Autowired private EntityManager entityManager;

    @Test
    void 모든_등급과_단계를_쉬운순으로_정렬한다() {
        // given
        Workbook workbook = workbooks.save(Workbook.builder().name("전체 난이도").build());
        List<Long> expected = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            Problem problem = problems.save(problem(difficulty));
            expected.add(problem.getId());
        }
        List<Long> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        for (Long id : reversed) {
            links.save(WorkbookProblem.builder().workbook(workbook).problem(problems.getReferenceById(id)).build());
        }
        entityManager.flush();
        entityManager.clear();
        // when
        var result = links.findSummaries(workbook.getId(), PageRequest.of(0, 100));
        // then
        assertThat(result.getContent()).extracting(WorkbookProblemRepository.Summary::getId).containsExactlyElementsOf(expected);
        assertThat(result.getTotalElements()).isEqualTo(31);
    }

    @Test
    void 같은_문제집과_문제_조합은_DB에서도_중복을_막는다() {
        // given
        Workbook workbook = workbooks.save(Workbook.builder().name("스택").build());
        Problem problem = problems.save(problem(Difficulty.UNRATED));
        links.saveAndFlush(WorkbookProblem.builder().workbook(workbook).problem(problem).build());
        // when & then
        assertThatThrownBy(() -> links.saveAndFlush(WorkbookProblem.builder().workbook(workbook).problem(problem).build()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB에서_난이도를_생략해도_미지정으로_저장한다() {
        // when
        entityManager.createNativeQuery("insert into problems (title,description,category,memory_limit_mb,time_limit_ms) "
            + "values ('기존형식','설명','구현',256,1000)").executeUpdate();
        // then
        assertThat(entityManager.createNativeQuery("select difficulty from problems where title='기존형식'", String.class)
            .getSingleResult()).isEqualTo("UNRATED");
    }

    private Problem problem(Difficulty difficulty) {
        return Problem.builder().title(difficulty.name()).description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).difficulty(difficulty).build();
    }
}
