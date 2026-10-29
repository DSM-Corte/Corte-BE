package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeUnavailableException extends CorteException {
    public JudgeUnavailableException() {
        super(JudgeErrorCode.JUDGE_UNAVAILABLE);
    }
}
