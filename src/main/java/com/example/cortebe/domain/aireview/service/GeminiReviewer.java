package com.example.cortebe.domain.aireview.service;

import com.example.cortebe.domain.aireview.GeminiProperties;
import com.example.cortebe.domain.aireview.client.GeminiClient;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Candidate;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Content;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentRequest;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerateContentResponse;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.GenerationConfig;
import com.example.cortebe.domain.aireview.client.dto.GeminiDto.Part;
import com.example.cortebe.domain.aireview.dto.AiReviewResponse.Comment;
import com.example.cortebe.domain.aireview.dto.AiReviewResponse.Suggestion;
import com.example.cortebe.domain.aireview.exception.AiReviewCallFailedException;
import com.example.cortebe.domain.aireview.exception.AiReviewInvalidResponseException;
import com.example.cortebe.domain.aireview.exception.AiReviewNotConfiguredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiReviewer {

    static final int MAX_COMMENTS = 4;

    private final GeminiClient client;
    private final GeminiProperties properties;
    private final ObjectMapper mapper;

    public record Source(String problemTitle, String language, long runtimeMs, String code) {
    }

    public record Result(String model, String summary, List<Comment> comments, Suggestion suggestion) {
    }

    public void ensureConfigured() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiReviewNotConfiguredException();
        }
    }

    public Result review(Source source) {
        ensureConfigured();
        String text = call(source);
        return normalize(parse(text), source);
    }

    private String call(Source source) {
        GenerateContentRequest request = new GenerateContentRequest(
            List.of(new Content("user", List.of(new Part(buildPrompt(source))))),
            new GenerationConfig("application/json", 0.25));
        GenerateContentResponse response;
        try {
            response = client.generateContent(properties.apiKey(), properties.model(), request);
        } catch (RuntimeException e) {
            // 키가 들어간 요청 정보는 로그에 남기지 않는다
            log.warn("Gemini 호출에 실패했습니다. 원인: {}", e.getClass().getSimpleName());
            throw new AiReviewCallFailedException();
        }
        String text = extractText(response);
        if (text.isBlank()) {
            throw new AiReviewInvalidResponseException();
        }
        return text;
    }

    private String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return "";
        }
        Candidate candidate = response.candidates().get(0);
        if (candidate == null || candidate.content() == null || candidate.content().parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part part : candidate.content().parts()) {
            if (part != null && part.text() != null) {
                text.append(part.text());
            }
        }
        return text.toString().trim();
    }

    private JsonNode parse(String text) {
        String stripped = text.replaceFirst("(?i)^```(?:json)?", "").replaceFirst("```$", "").trim();
        try {
            return requireObject(mapper.readTree(stripped));
        } catch (RuntimeException e) {
            int start = stripped.indexOf('{');
            int end = stripped.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new AiReviewInvalidResponseException();
            }
            try {
                return requireObject(mapper.readTree(stripped.substring(start, end + 1)));
            } catch (RuntimeException inner) {
                throw new AiReviewInvalidResponseException();
            }
        }
    }

    private JsonNode requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new AiReviewInvalidResponseException();
        }
        return node;
    }

    private Result normalize(JsonNode payload, Source source) {
        int maxLine = Math.max(source.code().split("\n", -1).length, 1);
        List<Comment> comments = new ArrayList<>();
        for (JsonNode node : payload.path("comments")) {
            String body = node.path("body").asString("").trim();
            if (!body.isEmpty() && comments.size() < MAX_COMMENTS) {
                comments.add(new Comment(clampLine(node.path("line"), maxLine), body));
            }
        }
        JsonNode suggestion = payload.path("suggestion");
        String code = suggestion.path("code").asString("").trim();
        return new Result(properties.model(),
            orDefault(payload.path("summary").asString(""), "제출 코드를 바탕으로 리뷰를 생성했습니다."),
            comments,
            new Suggestion(
                orDefault(suggestion.path("title").asString(""), "개선 제안"),
                orDefault(suggestion.path("description").asString(""), "제출 코드를 바탕으로 생성한 개선 제안입니다."),
                code.isEmpty() ? source.code() : code));
    }

    private int clampLine(JsonNode line, int maxLine) {
        if (!line.isNumber()) {
            return 1;
        }
        return (int) Math.min(Math.max(Math.round(line.asDouble()), 1), maxLine);
    }

    private String orDefault(String value, String fallback) {
        return value.isBlank() ? fallback : value.trim();
    }

    private String buildPrompt(Source source) {
        return String.join("\n",
            "너는 알고리즘 풀이 코드 리뷰어다.",
            "응답은 반드시 한국어 JSON 객체 하나로만 작성한다.",
            "마크다운 코드펜스나 설명 문장은 쓰지 않는다.",
            "스키마:",
            "{",
            "  \"summary\": \"전체 리뷰 요약\",",
            "  \"comments\": [{\"line\": 1, \"body\": \"라인 코멘트\"}],",
            "  \"suggestion\": {\"title\": \"개선 제안 제목\", \"description\": \"제안 설명\", "
                + "\"code\": \"개선된 전체 코드 또는 핵심 패치\"}",
            "}",
            "라인 코멘트는 최대 " + MAX_COMMENTS + "개로 제한하고, 실제 코드 줄 번호를 사용한다.",
            "동작을 바꾸는 수정은 근거가 분명할 때만 제안한다.",
            "",
            "문제: " + source.problemTitle(),
            "언어: " + source.language(),
            "실행 시간: " + source.runtimeMs() + "ms",
            "",
            "제출 코드:",
            "```",
            source.code(),
            "```");
    }
}
