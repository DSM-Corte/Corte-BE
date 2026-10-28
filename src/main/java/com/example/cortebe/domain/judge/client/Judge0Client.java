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

    @PostMapping("/submissions/batch?base64_encoded=true")
    List<TokenResponse> createSubmissions(@RequestBody BatchRequest request);

    @GetMapping("/submissions/batch?base64_encoded=true&fields=token,status,time,memory")
    BatchResponse getSubmissions(@RequestParam("tokens") String tokens);
}
