package com.example.cortebe.domain.workbook.dto;

import com.example.cortebe.domain.workbook.entity.Workbook;
import com.example.cortebe.domain.workbook.repository.WorkbookProblemRepository;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

@Builder
public record WorkbookDetailResponse(Long id, String name, long problemCount, ProblemPage problems) {

    public static WorkbookDetailResponse from(Workbook workbook, Page<WorkbookProblemRepository.Summary> problems) {
        return WorkbookDetailResponse.builder().id(workbook.getId()).name(workbook.getName())
            .problemCount(problems.getTotalElements()).problems(ProblemPage.from(problems)).build();
    }

    @Builder
    public record ProblemPage(List<Summary> content, int page, int size, long totalElements, int totalPages) {

        public static ProblemPage from(Page<WorkbookProblemRepository.Summary> problems) {
            return ProblemPage.builder()
                .content(problems.stream().map(row -> Summary.builder().id(row.getId()).title(row.getTitle()).build()).toList())
                .page(problems.getNumber()).size(problems.getSize())
                .totalElements(problems.getTotalElements()).totalPages(problems.getTotalPages()).build();
        }
    }

    @Builder
    public record Summary(Long id, String title) {
    }
}
