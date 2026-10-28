package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeConflictException extends CorteException {
    public JudgeConflictException() {
        super(JudgeErrorCode.JUDGE_CONFLICT);
    }
}
