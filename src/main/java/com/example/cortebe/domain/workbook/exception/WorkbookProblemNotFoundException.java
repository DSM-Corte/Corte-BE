package com.example.cortebe.domain.workbook.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class WorkbookProblemNotFoundException extends CorteException {
    public WorkbookProblemNotFoundException() {
        super(WorkbookErrorCode.WORKBOOK_PROBLEM_NOT_FOUND);
    }
}
