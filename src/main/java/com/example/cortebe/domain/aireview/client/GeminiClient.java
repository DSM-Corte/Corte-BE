package com.example.cortebe.domain.aireview.client;

import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentRequest;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "gemini", url = "${gemini.api-url:https://generativelanguage.googleapis.com}")
public interface GeminiClient {

    @PostMapping("/v1beta/models/{model}:generateContent")
    GenerateContentResponse generateContent(
        @RequestHeader("x-goog-api-key") String apiKey,
        @PathVariable("model") String model,
        @RequestBody GenerateContentRequest request);
}
