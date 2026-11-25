package com.example.cortebe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CorteBeApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void 서버_시간대가_UTC여도_한국_시간으로_고정한다() {
        // given
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        try {
            // when
            CorteBeApplication.useKoreaTimeZone();

            // then
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
        } finally {
            TimeZone.setDefault(original);
        }
    }

}
