package com.example.cortebe.domain.workbook.dto;

import com.example.cortebe.domain.workbook.entity.Workbook;
import lombok.Builder;

@Builder
public record WorkbookResponse(Long id, String name, long problemCount) {

    public static WorkbookResponse from(Workbook workbook, long problemCount) {
        return WorkbookResponse.builder().id(workbook.getId()).name(workbook.getName()).problemCount(problemCount).build();
    }
}
