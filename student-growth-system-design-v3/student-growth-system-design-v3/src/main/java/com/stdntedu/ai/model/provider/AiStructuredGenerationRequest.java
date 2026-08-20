package com.stdntedu.ai.model.provider;

public record AiStructuredGenerationRequest(String prompt) {
    public AiStructuredGenerationRequest {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
    }
}
