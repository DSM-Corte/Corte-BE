package com.example.cortebe.domain.judge.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTests {

    @Test
    void 공백과_마지막_줄바꿈까지_일치해야_정답이다() {
        // given
        String expected = "3\n";
        // when & then
        assertThat(OutputComparator.matches(expected, "3\n")).isTrue();
        assertThat(OutputComparator.matches(expected, "3")).isFalse();
        assertThat(OutputComparator.matches(expected, "3\r\n")).isFalse();
        assertThat(OutputComparator.matches(expected, "3 \n")).isFalse();
        assertThat(OutputComparator.matches("", "")).isTrue();
    }
}
