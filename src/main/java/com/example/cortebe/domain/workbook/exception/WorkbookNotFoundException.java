package com.example.cortebe.domain.workbook.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class WorkbookNotFoundException extends CorteException {
    public WorkbookNotFoundException() {
        super(WorkbookErrorCode.WORKBOOK_NOT_FOUND);
    }
}
