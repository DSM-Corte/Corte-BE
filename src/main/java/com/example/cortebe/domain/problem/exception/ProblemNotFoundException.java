package com.example.cortebe.domain.problem.exception;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;

public class ProblemNotFoundException extends CorteException {
    public ProblemNotFoundException() {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }
}
