package com.example.cortebe.domain.workbook.dto;

import com.example.cortebe.domain.workbook.repository.WorkbookRepository;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

@Builder
public record WorkbookPageResponse(List<Summary> content, int page, int size, long totalElements, int totalPages) {

    public static WorkbookPageResponse from(Page<WorkbookRepository.Summary> workbooks, Map<Long, Long> counts) {
        return WorkbookPageResponse.builder()
            .content(workbooks.stream().map(row -> Summary.builder().id(row.getId()).name(row.getName())
                .problemCount(counts.getOrDefault(row.getId(), 0L)).build()).toList())
            .page(workbooks.getNumber()).size(workbooks.getSize())
            .totalElements(workbooks.getTotalElements()).totalPages(workbooks.getTotalPages()).build();
    }

    @Builder
    public record Summary(Long id, String name, long problemCount) {
    }
}
