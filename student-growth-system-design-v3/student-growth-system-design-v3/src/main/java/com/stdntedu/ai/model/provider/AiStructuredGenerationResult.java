package com.stdntedu.ai.model.provider;

public record AiStructuredGenerationResult(String content, Integer promptTokens, Integer completionTokens) {
}
