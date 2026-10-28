package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeLimitException extends CorteException {
    public JudgeLimitException() {
        super(JudgeErrorCode.JUDGE_LIMIT);
    }
}
