package com.example.cortebe.domain.judge.client;

import com.example.cortebe.domain.judge.client.dto.Judge0Dto.BatchRequest;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.BatchResponse;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.TokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "judge0", url = "${judge0.url}")
public interface Judge0Client {

    // 경로에 쉼표가 든 쿼리를 직접 쓰면 Feign이 값을 여러 개로 나누므로 파라미터로 넘긴다
    String SUBMISSION_FIELDS = "token,status,time,memory,stdout,stderr,compile_output";

    @PostMapping("/submissions/batch?base64_encoded=true")
    List<TokenResponse> createSubmissions(@RequestBody BatchRequest request);

    @GetMapping("/submissions/batch?base64_encoded=true")
    BatchResponse getSubmissions(@RequestParam("tokens") String tokens, @RequestParam("fields") String fields);
}
