package com.stdntedu.ai.extraction.provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiExtractionProviderResultParser {
    private final ObjectMapper objectMapper;

    public AiExtractionProviderResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiExtractionProviderResult parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray()) throw invalid();
            List<AiExtractedQuestion> questions = new ArrayList<>();
            for (JsonNode node : questionsNode) questions.add(question(node));
            if (questions.isEmpty()) throw invalid();
            return new AiExtractionProviderResult(List.copyOf(questions));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof AiProviderException provider) throw provider;
            throw invalid();
        }
    }

    private AiExtractedQuestion question(JsonNode node) {
        String text = nullableText(node, "questionText");
        if (text == null || text.isBlank()) throw invalid();
        Integer difficulty = nullableInteger(node, "difficulty");
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) throw invalid();
        BigDecimal confidence = nullableDecimal(node, "confidence");
        range(confidence);
        return new AiExtractedQuestion(nullableInteger(node, "pageNumber"), nullableText(node, "questionNumber"),
                nullableText(node, "questionType"), text.trim(), nullableText(node, "studentAnswer"),
                nullableText(node, "correctAnswer"), nullableText(node, "answerSource"),
                nullableText(node, "analysisText"), nullableText(node, "analysisSource"),
                nullableText(node, "errorType"), difficulty, confidence, candidates(node.path("knowledgeCandidates")));
    }

    private List<AiKnowledgeCandidate> candidates(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw invalid();
        List<AiKnowledgeCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode candidate : node) {
            String id = nullableText(candidate, "knowledgeId");
            String code = nullableText(candidate, "knowledgeCode");
            String name = nullableText(candidate, "knowledgeName");
            if ((id == null || id.isBlank()) && (name == null || name.isBlank())) throw invalid();
            String key = id != null ? "id:" + id : "name:" + name + ":" + code;
            if (!seen.add(key)) continue;
            BigDecimal confidence = nullableDecimal(candidate, "confidence");
            range(confidence);
            result.add(new AiKnowledgeCandidate(id, code, name, confidence,
                    candidate.path("primary").asBoolean(false)));
        }
        return List.copyOf(result);
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.asText();
    }

    private Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToInt() || !value.isIntegralNumber()) throw invalid();
        return value.intValue();
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw invalid();
        return value.decimalValue();
    }

    private void range(BigDecimal value) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw invalid();
        }
    }

    private AiProviderException invalid() {
        return new AiProviderException("PROVIDER_RESPONSE_INVALID", "provider response was invalid");
    }
}
