package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class JudgeSubmitFailedException extends CorteException {

    public JudgeSubmitFailedException() {
        super(JudgeErrorCode.JUDGE_SUBMIT_FAILED);
    }
}
