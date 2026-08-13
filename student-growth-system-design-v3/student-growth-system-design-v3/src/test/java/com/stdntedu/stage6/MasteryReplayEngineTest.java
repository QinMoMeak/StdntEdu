package com.stdntedu.stage6;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.knowledge.mastery.algorithm.MasteryAlgorithmConfig;
import com.stdntedu.knowledge.mastery.algorithm.MasteryCalculationResult;
import com.stdntedu.knowledge.mastery.algorithm.MasteryCalculator;
import com.stdntedu.knowledge.mastery.algorithm.MasteryReplayEngine;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidence;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceResult;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceType;
import org.junit.jupiter.api.Test;

class MasteryReplayEngineTest {
    private static final LocalDateTime DAY_ONE = LocalDateTime.of(2026, 1, 1, 9, 0);
    private final MasteryReplayEngine engine = new MasteryReplayEngine(new MasteryCalculator());

    @Test void firstCorrectUsesConfiguredInitialScore() { assertScore("60.00", one(MasteryEvidenceResult.CORRECT)); }
    @Test void firstPartialUsesConfiguredInitialScore() { assertScore("45.00", one(MasteryEvidenceResult.PARTIAL)); }
    @Test void firstWrongUsesConfiguredInitialScore() { assertScore("30.00", one(MasteryEvidenceResult.WRONG)); }
    @Test void firstUnknownUsesConfiguredInitialScore() { assertScore("20.00", one(MasteryEvidenceResult.UNKNOWN)); }

    @Test void secondExamUsesExamDelta() {
        assertScore("70.00", List.of(item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT)));
    }

    @Test void reviewCorrectUsesReviewDelta() {
        assertScore("38.00", List.of(item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT)));
    }

    @Test void reviewUnknownIsNotTreatedAsWrongOrCorrect() {
        assertScore("46.00", List.of(item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.UNKNOWN)));
    }

    @Test void comparatorOrdersByEventTime() {
        List<MasteryEvidence> items = new ArrayList<>(List.of(
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(1, DAY_ONE, MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT)));
        items.sort(MasteryEvidence.ORDER);
        assertThat(items).extracting(MasteryEvidence::businessId).containsExactly(1L, 2L);
    }

    @Test void comparatorOrdersExamBeforePracticeAtSameTime() {
        List<MasteryEvidence> items = sameTime(MasteryEvidenceType.PRACTICE, MasteryEvidenceType.EXAM);
        assertThat(items).extracting(MasteryEvidence::evidenceType)
                .containsExactly(MasteryEvidenceType.EXAM, MasteryEvidenceType.PRACTICE);
    }

    @Test void comparatorOrdersPracticeBeforeReviewAtSameTime() {
        List<MasteryEvidence> items = sameTime(MasteryEvidenceType.REVIEW, MasteryEvidenceType.PRACTICE);
        assertThat(items).extracting(MasteryEvidence::evidenceType)
                .containsExactly(MasteryEvidenceType.PRACTICE, MasteryEvidenceType.REVIEW);
    }

    @Test void comparatorOrdersSameTypeByBusinessId() {
        List<MasteryEvidence> items = new ArrayList<>(List.of(
                item(9, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(3, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT)));
        items.sort(MasteryEvidence.ORDER);
        assertThat(items).extracting(MasteryEvidence::businessId).containsExactly(3L, 9L);
    }

    @Test void positivePerEventLimitIsApplied() {
        MasteryAlgorithmConfig config = config(Map.of("REVIEW.CORRECT", new BigDecimal("40")));
        assertThat(replay(List.of(item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT)), config)
                .masteryScore()).isEqualByComparingTo("45.00");
    }

    @Test void negativePerEventLimitIsApplied() {
        MasteryAlgorithmConfig config = config(Map.of("REVIEW.UNKNOWN", new BigDecimal("-40")));
        assertThat(replay(List.of(item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.UNKNOWN)), config)
                .masteryScore()).isEqualByComparingTo("40.00");
    }

    @Test void sameDayPositiveChangesUseSeparateCumulativeLimit() {
        List<MasteryEvidence> items = new ArrayList<>();
        items.add(item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG));
        for (int i = 1; i <= 3; i++) items.add(item(i + 1, DAY_ONE.plusHours(i), MasteryEvidenceType.REVIEW,
                MasteryEvidenceResult.CORRECT));
        assertScore("50.00", items);
    }

    @Test void sameDayNegativeChangesUseSeparateCumulativeLimit() {
        List<MasteryEvidence> items = new ArrayList<>();
        items.add(item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT));
        for (int i = 1; i <= 3; i++) items.add(item(i + 1, DAY_ONE.plusHours(i), MasteryEvidenceType.REVIEW,
                MasteryEvidenceResult.UNKNOWN));
        assertScore("35.00", items);
    }

    @Test void dailyLimitsResetAcrossLocalDates() {
        List<MasteryEvidence> items = List.of(
                item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(3, DAY_ONE.plusHours(2), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(4, DAY_ONE.plusDays(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(5, DAY_ONE.plusDays(1).plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT));
        assertScore("62.00", items);
    }

    @Test void scoreIsClampedAtOneHundred() {
        List<MasteryEvidence> items = new ArrayList<>();
        items.add(item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT));
        for (int i = 1; i <= 6; i++) items.add(item(i + 1, DAY_ONE.plusDays(i), MasteryEvidenceType.EXAM,
                MasteryEvidenceResult.CORRECT));
        assertScore("100.00", items);
    }

    @Test void scoreIsClampedAtZero() {
        List<MasteryEvidence> items = new ArrayList<>();
        items.add(item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG));
        for (int i = 1; i <= 3; i++) items.add(item(i + 1, DAY_ONE.plusDays(i), MasteryEvidenceType.REVIEW,
                MasteryEvidenceResult.UNKNOWN));
        assertScore("0.00", items);
    }

    @Test void replayRecomputesCountsStreaksAndTimesFromEvidence() {
        List<MasteryEvidence> items = List.of(
                item(1, DAY_ONE, MasteryEvidenceType.EXAM, MasteryEvidenceResult.CORRECT),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG),
                item(3, DAY_ONE.plusHours(2), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.PARTIAL),
                item(4, DAY_ONE.plusHours(3), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(5, DAY_ONE.plusHours(4), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(6, DAY_ONE.plusHours(5), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.UNKNOWN));
        MasteryCalculationResult result = replay(items, config(Map.of()));
        assertThat(result.evidenceCount()).isEqualTo(6);
        assertThat(result.correctCount()).isEqualTo(3);
        assertThat(result.partialCount()).isEqualTo(1);
        assertThat(result.wrongCount()).isEqualTo(1);
        assertThat(result.reviewCount()).isEqualTo(4);
        assertThat(result.continuousCorrectCount()).isZero();
        assertThat(result.continuousWrongCount()).isZero();
        assertThat(result.lastPracticeTime()).isEqualTo(DAY_ONE.plusHours(1));
        assertThat(result.lastReviewTime()).isEqualTo(DAY_ONE.plusHours(5));
        assertThat(result.decayBaseTime()).isEqualTo(DAY_ONE.plusHours(5));
    }

    @Test void repeatedReplayIsDeterministic() {
        List<MasteryEvidence> items = List.of(
                item(3, DAY_ONE.plusHours(2), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.CORRECT),
                item(1, DAY_ONE, MasteryEvidenceType.PRACTICE, MasteryEvidenceResult.WRONG),
                item(2, DAY_ONE.plusHours(1), MasteryEvidenceType.REVIEW, MasteryEvidenceResult.PARTIAL));
        assertThat(replay(items, config(Map.of()))).isEqualTo(replay(items, config(Map.of())));
    }

    private List<MasteryEvidence> one(MasteryEvidenceResult result) {
        return List.of(item(1, DAY_ONE, result == MasteryEvidenceResult.UNKNOWN ? MasteryEvidenceType.REVIEW
                : MasteryEvidenceType.EXAM, result));
    }

    private List<MasteryEvidence> sameTime(MasteryEvidenceType first, MasteryEvidenceType second) {
        List<MasteryEvidence> result = new ArrayList<>(List.of(item(1, DAY_ONE, first, MasteryEvidenceResult.WRONG),
                item(2, DAY_ONE, second, MasteryEvidenceResult.WRONG)));
        result.sort(MasteryEvidence.ORDER);
        return result;
    }

    private MasteryEvidence item(long id, LocalDateTime time, MasteryEvidenceType type, MasteryEvidenceResult result) {
        return new MasteryEvidence(type, id, time, result, 1L, 1L, null, Map.of());
    }

    private void assertScore(String expected, List<MasteryEvidence> evidence) {
        assertThat(replay(evidence, config(Map.of())).masteryScore()).isEqualByComparingTo(expected);
    }

    private MasteryCalculationResult replay(List<MasteryEvidence> evidence, MasteryAlgorithmConfig config) {
        return engine.replay(evidence, DAY_ONE.plusDays(7), config);
    }

    private MasteryAlgorithmConfig config(Map<String, BigDecimal> overrides) {
        Map<MasteryEvidenceResult, BigDecimal> initial = Map.of(
                MasteryEvidenceResult.CORRECT, new BigDecimal("60"),
                MasteryEvidenceResult.PARTIAL, new BigDecimal("45"),
                MasteryEvidenceResult.WRONG, new BigDecimal("30"),
                MasteryEvidenceResult.UNKNOWN, new BigDecimal("20"));
        Map<String, BigDecimal> changes = new LinkedHashMap<>();
        changes.put("EXAM.CORRECT", new BigDecimal("10"));
        changes.put("EXAM.PARTIAL", new BigDecimal("4"));
        changes.put("EXAM.WRONG", new BigDecimal("-12"));
        changes.put("PRACTICE.CORRECT", new BigDecimal("6"));
        changes.put("PRACTICE.PARTIAL", new BigDecimal("2"));
        changes.put("PRACTICE.WRONG", new BigDecimal("-8"));
        changes.put("REVIEW.CORRECT", new BigDecimal("8"));
        changes.put("REVIEW.PARTIAL", new BigDecimal("3"));
        changes.put("REVIEW.WRONG", new BigDecimal("-10"));
        changes.put("REVIEW.UNKNOWN", new BigDecimal("-14"));
        changes.putAll(overrides);
        return new MasteryAlgorithmConfig("1.0", new BigDecimal("0.80"), new BigDecimal("0.60"), false,
                ZoneId.of("Asia/Shanghai"), initial, changes, new BigDecimal("15"), new BigDecimal("20"),
                new BigDecimal("20"), new BigDecimal("25"), Map.of("mastery.algorithm.version", "1.0"));
    }
}
