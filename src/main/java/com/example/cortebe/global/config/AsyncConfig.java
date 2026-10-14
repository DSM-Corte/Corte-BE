package com.example.cortebe.global.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Configuration
public class AsyncConfig {

    // 종료 시 큐에 남은 비동기 작업(GitHub 저장 등)을 버리지 않고 끝날 때까지 기다린다
    @Bean
    public ThreadPoolTaskExecutorCustomizer shutdownGracefullyCustomizer() {
        return executor -> {
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);
        };
    }
}
