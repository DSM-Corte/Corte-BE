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
    void 긴_설명과_제한값을_저장하고_변경_감지로_수정한_뒤_삭제한다() {
        String description = "설명\n".repeat(3000);
        Problem problem = problemRepository.saveAndFlush(
            Problem.builder().title("문제").description(description).category("구현")
                .memoryLimitMb(256).timeLimitMs(1000).build());
        Long id = problem.getId();
        entityManager.clear();

        Problem loaded = problemRepository.findById(id).orElseThrow();
        assertEquals(description, loaded.getDescription());
        assertEquals("구현", loaded.getCategory());
        assertEquals(256, loaded.getMemoryLimitMb());
        assertEquals(1000, loaded.getTimeLimitMs());
        loaded.update("수정", null, "그래프", 512, 2000);
        problemRepository.flush();
        entityManager.clear();

        Problem updated = problemRepository.findById(id).orElseThrow();
        assertEquals("수정", updated.getTitle());
        assertEquals(description, updated.getDescription());
        assertEquals("그래프", updated.getCategory());
        assertEquals(512, updated.getMemoryLimitMb());
        assertEquals(2000, updated.getTimeLimitMs());
        problemRepository.delete(updated);
        problemRepository.flush();
        entityManager.clear();
        assertFalse(problemRepository.existsById(id));
    }

    @Test
    void 목록은_ID_내림차순으로_페이징한다() {
        problemRepository.save(Problem.builder().title("First").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).build());
        Problem second = problemRepository.saveAndFlush(
            Problem.builder().title("Second").description("Description").category("그래프")
                .memoryLimitMb(256).timeLimitMs(1000).build());
        entityManager.clear();

        var page = problemRepository.findAllBy(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(1, page.getContent().size());
        assertEquals(second.getId(), page.getContent().getFirst().getId());
        assertEquals("Second", page.getContent().getFirst().getTitle());
        assertEquals("그래프", page.getContent().getFirst().getCategory());
    }

    @Test
    void 카테고리에_해당하는_문제만_페이징한다() {
        problemRepository.save(Problem.builder().title("Other").description("Description")
            .category("그래프").memoryLimitMb(256).timeLimitMs(1000).build());
        problemRepository.save(Problem.builder().title("First").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).build());
        Problem last = problemRepository.saveAndFlush(Problem.builder().title("Last").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).build());
        entityManager.clear();

        var page = problemRepository.findAllByCategory("구현",
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(last.getId(), page.getContent().getFirst().getId());
        assertEquals("구현", page.getContent().getFirst().getCategory());
        assertEquals(0, problemRepository.findAllByCategory("없는분류", PageRequest.of(0, 20)).getTotalElements());
    }
}
