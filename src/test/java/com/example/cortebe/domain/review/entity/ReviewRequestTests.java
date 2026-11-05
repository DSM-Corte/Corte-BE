package com.example.cortebe.domain.review.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestTests {

    @Test
    void 마지막_줄바꿈_뒤의_빈_줄은_줄_수에_세지_않는다() {
        // given
        ReviewRequest withTrailingNewline = request("a\nb\n");
        ReviewRequest withoutTrailingNewline = request("a\nb");

        // when & then
        assertThat(withTrailingNewline.lineCount()).isEqualTo(2);
        assertThat(withoutTrailingNewline.lineCount()).isEqualTo(2);
    }

    @Test
    void 지정한_줄만_바꾼_코드를_만든다() {
        // given
        ReviewRequest request = request("int a = 1;\nint b = 2;\nint c = 3;\n");

        // when
        String replaced = request.replaceLine(2, "int b = 20;");

        // then
        assertThat(replaced).isEqualTo("int a = 1;\nint b = 20;\nint c = 3;\n");
    }

    @Test
    void 줄을_바꿔도_CRLF의_캐리지리턴은_유지한다() {
        // given
        ReviewRequest request = request("a\r\nb\r\n");

        // when
        String replaced = request.replaceLine(1, "x");

        // then
        assertThat(replaced).isEqualTo("x\r\nb\r\n");
    }

    @Test
    void 줄을_바꿔도_원본_코드는_바뀌지_않는다() {
        // given
        ReviewRequest request = request("a\nb");

        // when
        request.replaceLine(1, "x");

        // then
        assertThat(request.getCode()).isEqualTo("a\nb");
    }

    private ReviewRequest request(String code) {
        return ReviewRequest.builder().code(code).build();
    }
}
