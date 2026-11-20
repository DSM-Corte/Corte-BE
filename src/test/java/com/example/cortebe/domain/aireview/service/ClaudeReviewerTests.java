package com.example.cortebe.domain.aireview.service;

import com.example.cortebe.domain.aireview.ClaudeProperties;
import com.example.cortebe.domain.aireview.client.ClaudeClient;
import com.example.cortebe.domain.aireview.exception.AiReviewCallFailedException;
import com.example.cortebe.domain.aireview.exception.AiReviewInvalidResponseException;
import com.example.cortebe.domain.aireview.exception.AiReviewNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class ClaudeReviewerTests {

    private static final ClaudeReviewer.Source SOURCE =
        new ClaudeReviewer.Source("합", "PYTHON", 12, "a = 1\nb = 2\nprint(a + b)");

    private final ClaudeClient client = mock(ClaudeClient.class);

    private ClaudeReviewer reviewer(String apiKey) {
        return new ClaudeReviewer(client, new ClaudeProperties(apiKey, "test-model"), JsonMapper.builder().build());
    }

    @Test
    void 응답을_파싱하고_줄_번호와_개수를_보정한다() {
        // given
        String json = """
            ```json
            {"summary":"좋아요","comments":[
              {"line":99,"body":"범위 밖"},{"line":0,"body":"하한"},{"line":2,"body":""},
              {"line":1,"body":"a"},{"line":2,"body":"b"},{"line":3,"body":"c"}],
             "suggestion":{"title":"제안","description":"설명","code":"print(3)"}}
            ```""";
        given(client.complete(eq("key"), eq("test-model"), any())).willReturn(json);

        // when
        ClaudeReviewer.Result result = reviewer("key").review(SOURCE);

        // then
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.summary()).isEqualTo("좋아요");
        assertThat(result.comments()).hasSize(4);
        assertThat(result.comments().get(0).line()).isEqualTo(3);
        assertThat(result.comments().get(1).line()).isEqualTo(1);
        assertThat(result.suggestion().code()).isEqualTo("print(3)");
    }

    @Test
    void 제안_코드가_비어있으면_제출_코드를_쓴다() {
        // given
        given(client.complete(any(), any(), any()))
            .willReturn("{\"summary\":\"\",\"comments\":[]}");

        // when
        ClaudeReviewer.Result result = reviewer("key").review(SOURCE);

        // then
        assertThat(result.suggestion().code()).isEqualTo(SOURCE.code());
        assertThat(result.summary()).isNotBlank();
        assertThat(result.comments()).isEmpty();
    }

    @Test
    void 요청에_제출_코드와_JSON_응답_형식을_담는다() {
        // given
        given(client.complete(any(), any(), any())).willReturn("{}");

        // when
        reviewer("key").review(SOURCE);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(client).should().complete(eq("key"), eq("test-model"), captor.capture());
        assertThat(captor.getValue()).contains("JSON 객체 하나로만", "문제: 합", "언어: PYTHON", "print(a + b)");
    }

    @Test
    void 키가_비어있으면_호출하지_않고_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> reviewer(" ").review(SOURCE)).isInstanceOf(AiReviewNotConfiguredException.class);
        then(client).should(never()).complete(any(), any(), any());
    }

    @Test
    void 호출이_실패하면_예외를_던진다() {
        // given
        willThrow(new IllegalStateException("boom")).given(client).complete(any(), any(), any());

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewCallFailedException.class);
    }

    @Test
    void JSON이_아닌_응답이면_예외를_던진다() {
        // given
        given(client.complete(any(), any(), any())).willReturn("죄송합니다");

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 응답이_비어있으면_예외를_던진다() {
        // given
        given(client.complete(any(), any(), any())).willReturn("");

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewInvalidResponseException.class);
    }
}
