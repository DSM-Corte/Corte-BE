package com.example.cortebe.domain.judge.runner;

import com.example.cortebe.domain.judge.controller.ExecutionSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableScheduling
@EnableWebSocket
@RequiredArgsConstructor
public class JudgeSocketConfig implements WebSocketConfigurer {
    private final ExecutionSocket socket;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket, "/api/executions/{id}/stream")
            .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }
}
