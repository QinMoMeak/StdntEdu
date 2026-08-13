package com.stdntedu.knowledge.mastery.algorithm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record MasteryCalculationResult(
        BigDecimal masteryScore,
        int correctCount,
        int partialCount,
        int wrongCount,
        int reviewCount,
        int continuousCorrectCount,
        int continuousWrongCount,
        int evidenceCount,
        LocalDateTime lastPracticeTime,
        LocalDateTime lastReviewTime,
        LocalDateTime nextReviewTime,
        LocalDateTime decayBaseTime,
        String algorithmVersion,
        Map<String, Long> evidenceSummary) {

    public boolean hasEvidence() {
        return evidenceCount > 0;
    }
}
