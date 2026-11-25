package com.example.cortebe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@EnableFeignClients
@ConfigurationPropertiesScan
@SpringBootApplication
public class CorteBeApplication {

    public static void main(String[] args) {
        useKoreaTimeZone();
        SpringApplication.run(CorteBeApplication.class, args);
    }

    // 시각을 시간대 없이 저장·응답하므로, 실행 환경(Docker는 UTC)과 상관없이 한국 시간으로 맞춘다
    static void useKoreaTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

}
