package com.example.cortebe.domain.aireview.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ClaudeClient {

    // 같은 키로 만든 SDK 클라이언트는 연결 풀을 재사용한다
    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    public String complete(String apiKey, String model, String prompt) {
        AnthropicClient client = clients.computeIfAbsent(apiKey,
            key -> AnthropicOkHttpClient.builder().apiKey(key).build());
        Message response = client.messages().create(MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .addUserMessage(prompt)
            .build());
        return response.content().stream()
            .flatMap(block -> block.text().stream())
            .map(TextBlock::text)
            .collect(Collectors.joining())
            .trim();
    }
}
