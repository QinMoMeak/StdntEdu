package com.stdntedu.knowledge.mastery.algorithm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.knowledge.mastery.evidence.MasteryEvidence;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceResult;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceType;
import org.springframework.stereotype.Component;

@Component
public class MasteryReplayEngine {
    private final MasteryCalculator calculator;

    public MasteryReplayEngine(MasteryCalculator calculator) {
        this.calculator = calculator;
    }

    public MasteryCalculationResult replay(List<MasteryEvidence> source, LocalDateTime nextReviewTime,
            MasteryAlgorithmConfig config) {
        if (config.timeDecayEnabled()) {
            throw new IllegalStateException("unsupported mastery algorithm configuration: time decay is enabled");
        }
        List<MasteryEvidence> evidence = new ArrayList<>(source);
        evidence.sort(MasteryEvidence.ORDER);
        if (evidence.isEmpty()) return empty(nextReviewTime, config.algorithmVersion());

        BigDecimal score = calculator.initial(evidence.getFirst(), config);
        Map<LocalDate, BigDecimal> dailyPositive = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyNegative = new HashMap<>();
        for (int index = 1; index < evidence.size(); index++) {
            MasteryEvidence item = evidence.get(index);
            BigDecimal limited = calculator.eventLimitedChange(item, config);
            LocalDate day = item.eventTime().atZone(config.timezone()).toLocalDate();
            if (limited.signum() > 0) {
                BigDecimal remaining = config.maxDailyIncrease().subtract(dailyPositive.getOrDefault(day, BigDecimal.ZERO))
                        .max(BigDecimal.ZERO);
                limited = limited.min(remaining);
            } else if (limited.signum() < 0) {
                BigDecimal remaining = config.maxDailyDecrease().subtract(dailyNegative.getOrDefault(day, BigDecimal.ZERO))
                        .max(BigDecimal.ZERO);
                limited = limited.max(remaining.negate());
            }
            BigDecimal before = score;
            score = calculator.apply(score, limited);
            BigDecimal actual = score.subtract(before);
            if (actual.signum() > 0) dailyPositive.merge(day, actual, BigDecimal::add);
            if (actual.signum() < 0) dailyNegative.merge(day, actual.abs(), BigDecimal::add);
        }

        int correct = count(evidence, MasteryEvidenceResult.CORRECT);
        int partial = count(evidence, MasteryEvidenceResult.PARTIAL);
        int wrong = count(evidence, MasteryEvidenceResult.WRONG);
        int reviews = (int) evidence.stream().filter(item -> item.evidenceType() == MasteryEvidenceType.REVIEW).count();
        int continuousCorrect = trailing(evidence, MasteryEvidenceResult.CORRECT);
        int continuousWrong = trailing(evidence, MasteryEvidenceResult.WRONG);
        LocalDateTime lastPractice = latest(evidence, false);
        LocalDateTime lastReview = latest(evidence, true);
        Map<String, Long> summary = new LinkedHashMap<>();
        for (MasteryEvidenceType type : MasteryEvidenceType.values()) {
            summary.put(type.name(), evidence.stream().filter(item -> item.evidenceType() == type).count());
        }
        for (MasteryEvidenceResult result : MasteryEvidenceResult.values()) {
            summary.put(result.name(), evidence.stream().filter(item -> item.result() == result).count());
        }
        return new MasteryCalculationResult(score, correct, partial, wrong, reviews, continuousCorrect,
                continuousWrong, evidence.size(), lastPractice, lastReview, nextReviewTime,
                evidence.getLast().eventTime(), config.algorithmVersion(), Map.copyOf(summary));
    }

    private MasteryCalculationResult empty(LocalDateTime nextReviewTime, String version) {
        return new MasteryCalculationResult(null, 0, 0, 0, 0, 0, 0, 0, null, null, nextReviewTime, null,
                version, Map.of());
    }

    private int count(List<MasteryEvidence> evidence, MasteryEvidenceResult result) {
        return (int) evidence.stream().filter(item -> item.result() == result).count();
    }

    private int trailing(List<MasteryEvidence> evidence, MasteryEvidenceResult result) {
        int count = 0;
        for (int index = evidence.size() - 1; index >= 0 && evidence.get(index).result() == result; index--) count++;
        return count;
    }

    private LocalDateTime latest(List<MasteryEvidence> evidence, boolean review) {
        return evidence.stream().filter(item -> (item.evidenceType() == MasteryEvidenceType.REVIEW) == review)
                .map(MasteryEvidence::eventTime).max(LocalDateTime::compareTo).orElse(null);
    }
}
