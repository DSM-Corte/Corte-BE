package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeNotFoundException extends CorteException {
    public JudgeNotFoundException() {
        super(JudgeErrorCode.JUDGE_NOT_FOUND);
    }
}
