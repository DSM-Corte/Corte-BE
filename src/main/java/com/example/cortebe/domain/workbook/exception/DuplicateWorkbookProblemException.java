package com.example.cortebe.domain.workbook.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class DuplicateWorkbookProblemException extends CorteException {
    public DuplicateWorkbookProblemException() {
        super(WorkbookErrorCode.DUPLICATE_WORKBOOK_PROBLEM);
    }
}
