package com.example.cortebe.domain.problem.repository;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.service.ProblemService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Import;
import org.hibernate.SessionFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(ProblemService.class)
@ActiveProfiles("test")
class ProblemRepositoryTests {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProblemService problemService;

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
        loaded.update("수정", null, "그래프", 512, 2000, null);
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

    @Test
    void 여러_태그를_저장하고_교체한_뒤_태그와_문제를_삭제한다() {
        Problem problem = problemRepository.saveAndFlush(Problem.builder().title("Title").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열", "정렬")).build());
        Long id = problem.getId();
        entityManager.clear();

        Problem loaded = problemRepository.findById(id).orElseThrow();
        assertEquals(Set.of("배열", "정렬"), loaded.getTags());
        loaded.update(null, null, null, null, null, Set.of("탐색"));
        problemRepository.flush();
        entityManager.clear();
        assertEquals(Set.of("탐색"), problemRepository.findById(id).orElseThrow().getTags());

        problemRepository.deleteById(id);
        problemRepository.flush();
        entityManager.clear();
        assertFalse(problemRepository.existsById(id));
        assertEquals(0, problemRepository.findTagsByProblemIds(List.of(id)).size());
    }

    @Test
    void 태그와_카테고리로_필터링해도_문제가_중복되지_않고_전체_개수가_맞는다() {
        Problem first = problemRepository.save(Problem.builder().title("First").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열", "정렬")).build());
        problemRepository.save(Problem.builder().title("Other").description("Description")
            .category("그래프").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열", "탐색")).build());
        Problem last = problemRepository.save(Problem.builder().title("Last").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열", "탐색")).build());
        problemRepository.saveAndFlush(Problem.builder().title("Untagged").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).build());
        entityManager.clear();
        var pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"));

        var allCategories = problemRepository.findAllByTag(null, "배열", pageable);
        assertEquals(3, allCategories.getTotalElements());
        assertEquals(3, allCategories.getTotalPages());
        assertEquals(last.getId(), allCategories.getContent().getFirst().getId());

        var filtered = problemRepository.findAllByTag("구현", "배열", pageable);
        assertEquals(2, filtered.getTotalElements());
        assertEquals(2, filtered.getTotalPages());
        assertEquals(last.getId(), filtered.getContent().getFirst().getId());
        assertEquals(first.getId(), problemRepository.findAllByTag("구현", "배열",
            PageRequest.of(1, 1, pageable.getSort())).getContent().getFirst().getId());
        assertEquals(0, problemRepository.findAllByTag(null, "없는태그", pageable).getTotalElements());
    }

    @Test
    void 목록의_태그는_문제별로_추가_조회하지_않고_한번에_가져온다() {
        for (int i = 0; i < 6; i++) {
            problemRepository.save(Problem.builder().title("Problem " + i).description("Description")
                .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열", "정렬")).build());
        }
        problemRepository.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = problemService.list(0, 5, "구현", "배열");

        assertEquals(6, page.totalElements());
        assertEquals(5, page.content().size());
        page.content().forEach(problem -> assertEquals(Set.of("배열", "정렬"), problem.tags()));
        assertEquals(3, statistics.getPrepareStatementCount());
    }
}
