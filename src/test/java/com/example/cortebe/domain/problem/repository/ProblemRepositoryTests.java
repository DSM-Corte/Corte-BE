package com.example.cortebe.domain.problem.repository;

import com.example.cortebe.domain.problem.entity.Problem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest
@ActiveProfiles("test")
class ProblemRepositoryTests {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsLongTextAndDirtyCheckingUpdates() {
        String description = "설명\n".repeat(3000);
        Problem problem = problemRepository.saveAndFlush(
            new Problem("문제", description, "입력", "출력", "1 2\n", "3\n"));
        Long id = problem.getId();
        entityManager.clear();

        Problem loaded = problemRepository.findById(id).orElseThrow();
        assertEquals(description, loaded.getDescription());
        assertEquals("1 2\n", loaded.getExampleInput());
        loaded.update("수정", null, null, null, null, "");
        problemRepository.flush();
        entityManager.clear();

        Problem updated = problemRepository.findById(id).orElseThrow();
        assertEquals("수정", updated.getTitle());
        assertEquals(description, updated.getDescription());
        assertEquals("", updated.getExampleOutput());
        problemRepository.delete(updated);
        problemRepository.flush();
        entityManager.clear();
        assertFalse(problemRepository.existsById(id));
    }

    @Test
    void summaryQueryPaginatesInDescendingIdOrder() {
        problemRepository.save(new Problem("First", "Description", "Input", "Output", "", ""));
        Problem second = problemRepository.saveAndFlush(
            new Problem("Second", "Description", "Input", "Output", "", ""));
        entityManager.clear();

        var page = problemRepository.findAllBy(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(1, page.getContent().size());
        assertEquals(second.getId(), page.getContent().getFirst().getId());
        assertEquals("Second", page.getContent().getFirst().getTitle());
    }
}
