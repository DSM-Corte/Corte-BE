package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeForbiddenException extends CorteException {
    public JudgeForbiddenException() {
        super(JudgeErrorCode.JUDGE_FORBIDDEN);
    }
}
