package com.example.cortebe.domain.judge.runner;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionClockTests {
    @Test
    void 종료후_결과조회와_정리시간을_포함하지_않는다() {
        // given
        AtomicLong nano = new AtomicLong();
        ExecutionClock clock = new ExecutionClock(1000, 10000, nano::get);
        clock.start();
        nano.set(200_000_000L);
        clock.stop();
        // when
        nano.set(5_000_000_000L);
        // then
        assertThat(clock.elapsedMillis()).isEqualTo(200);
        assertThat(clock.expired()).isFalse();
    }
    @Test
    void 사용자_진입_전_준비시간은_실행시간과_제한에_포함하지_않는다() {
        // given
        AtomicLong nano = new AtomicLong();
        ExecutionClock clock = new ExecutionClock(1000, 10000, nano::get);
        nano.set(5_000_000_000L);
        // when & then
        assertThat(clock.elapsedMillis()).isZero();
        assertThat(clock.expired()).isFalse();
        assertThat(clock.startupExpired()).isFalse();
        clock.start();
        nano.set(5_200_000_000L);
        assertThat(clock.elapsedMillis()).isEqualTo(200);
        assertThat(clock.expired()).isFalse();
        nano.set(6_000_000_000L);
        assertThat(clock.expired()).isTrue();
    }

    @Test
    void 진입신호가_없어도_준비시간_상한으로_중단한다() {
        // given
        AtomicLong nano = new AtomicLong();
        ExecutionClock clock = new ExecutionClock(1000, 10000, nano::get);
        // when
        nano.set(10_000_000_000L);
        // then
        assertThat(clock.startupExpired()).isTrue();
        assertThat(clock.elapsedMillis()).isZero();
    }

    @Test
    void 진입신호를_반복해도_시간을_초기화하지_않는다() {
        // given
        AtomicLong nano = new AtomicLong();
        ExecutionClock clock = new ExecutionClock(1000, 10000, nano::get);
        clock.start();
        // when
        nano.set(1_000_000_000L);
        clock.start();
        // then
        assertThat(clock.expired()).isTrue();
    }
}
