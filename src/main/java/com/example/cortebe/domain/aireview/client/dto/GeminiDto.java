package com.example.cortebe.domain.aireview.client.dto;

import java.util.List;

public final class GeminiDto {

    private GeminiDto() {
    }

    public record GenerateContentRequest(List<Content> contents, GenerationConfig generationConfig) {
    }

    public record Content(String role, List<Part> parts) {
    }

    public record Part(String text) {
    }

    public record GenerationConfig(String responseMimeType, double temperature) {
    }

    public record GenerateContentResponse(List<Candidate> candidates) {
    }

    public record Candidate(Content content) {
    }
}
