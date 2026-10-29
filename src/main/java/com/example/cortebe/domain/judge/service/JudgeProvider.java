package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.JudgeRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;

public interface JudgeProvider {

    // 채점이 끝날 때까지 기다리므로 트랜잭션 밖에서 호출한다
    JudgeResult judge(JudgeRequest request);
}
