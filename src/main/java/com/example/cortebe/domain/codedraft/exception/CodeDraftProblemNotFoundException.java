package com.example.cortebe.domain.codedraft.exception;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;

public class CodeDraftProblemNotFoundException extends CorteException {

    public CodeDraftProblemNotFoundException() {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }
}
