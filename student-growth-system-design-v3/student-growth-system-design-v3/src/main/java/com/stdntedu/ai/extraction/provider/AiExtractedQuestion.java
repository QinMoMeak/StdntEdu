package com.stdntedu.ai.extraction.provider;

import java.math.BigDecimal;
import java.util.List;

public record AiExtractedQuestion(Integer pageNumber, String questionNumber, String questionType,
        String questionText, String studentAnswer, String correctAnswer, String answerSource,
        String analysisText, String analysisSource, String errorType, Integer difficulty,
        BigDecimal confidence, List<AiKnowledgeCandidate> knowledgeCandidates) { }
