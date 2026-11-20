package com.example.cortebe.domain.aireview.service;

import com.example.cortebe.domain.aireview.GeminiProperties;
import com.example.cortebe.domain.aireview.client.GeminiClient;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Candidate;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Content;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentRequest;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentResponse;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Part;
import com.example.cortebe.domain.aireview.exception.AiReviewCallFailedException;
import com.example.cortebe.domain.aireview.exception.AiReviewInvalidResponseException;
import com.example.cortebe.domain.aireview.exception.AiReviewNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class GeminiReviewerTests {

    private static final GeminiReviewer.Source SOURCE =
        new GeminiReviewer.Source("합", "PYTHON", 12, "a = 1\nb = 2\nprint(a + b)");

    private final GeminiClient client = mock(GeminiClient.class);

    private GeminiReviewer reviewer(String apiKey) {
        return new GeminiReviewer(client, new GeminiProperties(apiKey, "test-model"), JsonMapper.builder().build());
    }

    private GenerateContentResponse response(String text) {
        return new GenerateContentResponse(List.of(new Candidate(new Content("model", List.of(new Part(text))))));
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
        given(client.generateContent(eq("key"), eq("test-model"), any())).willReturn(response(json));

        // when
        GeminiReviewer.Result result = reviewer("key").review(SOURCE);

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
        given(client.generateContent(any(), any(), any()))
            .willReturn(response("{\"summary\":\"\",\"comments\":[]}"));

        // when
        GeminiReviewer.Result result = reviewer("key").review(SOURCE);

        // then
        assertThat(result.suggestion().code()).isEqualTo(SOURCE.code());
        assertThat(result.summary()).isNotBlank();
        assertThat(result.comments()).isEmpty();
    }

    @Test
    void 요청에_제출_코드와_JSON_응답_설정을_담는다() {
        // given
        given(client.generateContent(any(), any(), any())).willReturn(response("{}"));

        // when
        reviewer("key").review(SOURCE);

        // then
        ArgumentCaptor<GenerateContentRequest> captor = ArgumentCaptor.forClass(GenerateContentRequest.class);
        then(client).should().generateContent(eq("key"), eq("test-model"), captor.capture());
        assertThat(captor.getValue().generationConfig().responseMimeType()).isEqualTo("application/json");
        assertThat(captor.getValue().contents().get(0).parts().get(0).text())
            .contains("문제: 합", "언어: PYTHON", "print(a + b)");
    }

    @Test
    void 키가_비어있으면_호출하지_않고_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> reviewer(" ").review(SOURCE)).isInstanceOf(AiReviewNotConfiguredException.class);
        then(client).should(never()).generateContent(any(), any(), any());
    }

    @Test
    void 호출이_실패하면_예외를_던진다() {
        // given
        willThrow(new IllegalStateException("boom")).given(client).generateContent(any(), any(), any());

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewCallFailedException.class);
    }

    @Test
    void JSON이_아닌_응답이면_예외를_던진다() {
        // given
        given(client.generateContent(any(), any(), any())).willReturn(response("죄송합니다"));

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 응답이_비어있으면_예외를_던진다() {
        // given
        given(client.generateContent(any(), any(), any())).willReturn(new GenerateContentResponse(List.of()));

        // when & then
        assertThatThrownBy(() -> reviewer("key").review(SOURCE)).isInstanceOf(AiReviewInvalidResponseException.class);
    }
}
