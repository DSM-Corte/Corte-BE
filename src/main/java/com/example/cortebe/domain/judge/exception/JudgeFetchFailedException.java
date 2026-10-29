package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeFetchFailedException extends CorteException {

    public JudgeFetchFailedException() {
        super(JudgeErrorCode.JUDGE_FETCH_FAILED);
    }
}
