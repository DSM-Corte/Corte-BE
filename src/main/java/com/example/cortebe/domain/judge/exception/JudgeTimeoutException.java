package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeTimeoutException extends CorteException {

    public JudgeTimeoutException() {
        super(JudgeErrorCode.JUDGE_TIMEOUT);
    }
}
