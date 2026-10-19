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
    void createPreservesExamplesIncludingWhitespace() {
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = problemService.create(new CreateProblemRequest(
            "Sum", "Add numbers", "Two integers", "Their sum", "1 2\n", "3\n"));

        assertEquals("Sum", response.title());
        assertEquals("1 2\n", response.exampleInput());
        assertEquals("3\n", response.exampleOutput());
    }

    @Test
    void patchPreservesOmittedFieldsAndAllowsClearingExamples() {
        Problem problem = new Problem("Original", "Description", "Input", "Output", "1", "2");
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        var response = problemService.update(1L,
            new UpdateProblemRequest("Changed", null, null, null, "", null));

        assertEquals("Changed", response.title());
        assertEquals("Description", response.description());
        assertEquals("Input", response.inputFormat());
        assertEquals("Output", response.outputFormat());
        assertEquals("", response.exampleInput());
        assertEquals("2", response.exampleOutput());
    }

    @Test
    void missingProblemIsNotFoundForGetUpdateAndDelete() {
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
    void invalidPaginationDoesNotQueryDatabase() {
        assertThrows(CorteException.class, () -> problemService.list(-1, 20));
        assertThrows(CorteException.class, () -> problemService.list(0, 0));
        assertThrows(CorteException.class, () -> problemService.list(0, 101));
        verifyNoInteractions(problemRepository);
    }

    @Test
    void deleteRemovesExistingProblem() {
        Problem problem = new Problem("Title", "Description", "Input", "Output", "", "");
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        problemService.delete(1L);

        verify(problemRepository).delete(problem);
    }
}
