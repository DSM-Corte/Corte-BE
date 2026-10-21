package com.example.cortebe.domain.problem.service;

import com.example.cortebe.domain.problem.dto.CreateProblemRequest;
import com.example.cortebe.domain.problem.dto.UpdateProblemRequest;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTests {

    @Mock
    private ProblemRepository problemRepository;

    @InjectMocks
    private ProblemService problemService;

    @Test
    void 문제를_생성하면_카테고리와_실행_제한을_반환한다() {
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = problemService.create(new CreateProblemRequest(
            "Sum", "Add numbers", "구현", 256, 1000, Set.of(" 배열 ", "배열", "정렬")));

        assertEquals("Sum", response.title());
        assertEquals("구현", response.category());
        assertEquals(256, response.memoryLimitMb());
        assertEquals(1000, response.timeLimitMs());
        assertEquals(Set.of("배열", "정렬"), response.tags());
    }

    @Test
    void 부분_수정은_전달된_필드만_바꾸고_나머지는_유지한다() {
        Problem problem = Problem.builder().title("Original").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열")).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        var response = problemService.update(1L,
            new UpdateProblemRequest("Changed", null, "그래프", null, 2000, null));

        assertEquals("Changed", response.title());
        assertEquals("Description", response.description());
        assertEquals("그래프", response.category());
        assertEquals(256, response.memoryLimitMb());
        assertEquals(2000, response.timeLimitMs());
        assertEquals(Set.of("배열"), response.tags());
    }

    @Test
    void 없는_문제를_조회_수정_삭제하면_찾을_수_없음_오류가_발생한다() {
        when(problemRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(GlobalErrorCode.RESOURCE_NOT_FOUND,
            assertThrows(CorteException.class, () -> problemService.get(99L)).getErrorCode());
        assertEquals(GlobalErrorCode.RESOURCE_NOT_FOUND,
            assertThrows(CorteException.class, () -> problemService.update(99L,
                new UpdateProblemRequest("Changed", null, null, null, null, null))).getErrorCode());
        assertEquals(GlobalErrorCode.RESOURCE_NOT_FOUND,
            assertThrows(CorteException.class, () -> problemService.delete(99L)).getErrorCode());
    }

    @Test
    void 잘못된_페이지나_카테고리로_조회하면_DB에_접근하지_않는다() {
        assertThrows(CorteException.class, () -> problemService.list(-1, 20, null, null));
        assertThrows(CorteException.class, () -> problemService.list(0, 0, null, null));
        assertThrows(CorteException.class, () -> problemService.list(0, 101, null, null));
        assertThrows(CorteException.class, () -> problemService.list(0, 20, " ", null));
        assertThrows(CorteException.class, () -> problemService.list(0, 20, "x".repeat(51), null));
        assertThrows(CorteException.class, () -> problemService.list(0, 20, null, " "));
        assertThrows(CorteException.class, () -> problemService.list(0, 20, null, "x".repeat(31)));
        verifyNoInteractions(problemRepository);
    }

    @Test
    void 존재하는_문제를_삭제한다() {
        Problem problem = Problem.builder().title("Title").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        problemService.delete(1L);

        verify(problemRepository).delete(problem);
    }

    @Test
    void 태그를_전달하면_기존_태그를_교체하고_빈_배열이면_모두_삭제한다() {
        Problem problem = Problem.builder().title("Title").description("Description")
            .category("구현").memoryLimitMb(256).timeLimitMs(1000).tags(Set.of("배열")).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        var replaced = problemService.update(1L,
            new UpdateProblemRequest(null, null, null, null, null, Set.of(" 정렬 ", "정렬")));
        assertEquals(Set.of("정렬"), replaced.tags());

        var cleared = problemService.update(1L,
            new UpdateProblemRequest(null, null, null, null, null, Set.of()));
        assertEquals(Set.of(), cleared.tags());
        assertEquals(Set.of("정렬"), replaced.tags());
    }
}
