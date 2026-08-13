package com.stdntedu.knowledge.mastery.evidence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.knowledge.mastery.algorithm.MasteryAlgorithmConfig;
import com.stdntedu.knowledge.mastery.mapper.MasteryEvidenceMapper;
import org.springframework.stereotype.Component;

@Component
public class MasteryEvidenceAggregator {
    private final MasteryEvidenceMapper evidence;

    public MasteryEvidenceAggregator(MasteryEvidenceMapper evidence) {
        this.evidence = evidence;
    }

    public AggregatedEvidence aggregate(Long studentId, Long knowledgeId, MasteryAlgorithmConfig config) {
        List<MasteryEvidence> result = new ArrayList<>();
        for (MasteryEvidenceRow row : evidence.selectExamEvidence(studentId, knowledgeId)) {
            BigDecimal rate = rate(row);
            if (rate != null) result.add(toExam(row, rate, studentId, knowledgeId, config));
        }
        for (MasteryEvidenceRow row : evidence.selectPracticeEvidence(studentId, knowledgeId)) {
            result.add(direct(row, MasteryEvidenceType.PRACTICE, studentId, knowledgeId));
        }
        for (MasteryEvidenceRow row : evidence.selectReviewEvidence(studentId, knowledgeId)) {
            result.add(direct(row, MasteryEvidenceType.REVIEW, studentId, knowledgeId));
        }
        result.sort(MasteryEvidence.ORDER);
        LocalDateTime nextReview = evidence.selectNextReviewTime(studentId, knowledgeId);
        return new AggregatedEvidence(List.copyOf(result), nextReview);
    }

    private MasteryEvidence toExam(MasteryEvidenceRow row, BigDecimal rate, Long studentId, Long knowledgeId,
            MasteryAlgorithmConfig config) {
        MasteryEvidenceResult classification = rate.compareTo(config.correctMinimum()) >= 0
                ? MasteryEvidenceResult.CORRECT
                : rate.compareTo(config.partialMinimum()) >= 0 ? MasteryEvidenceResult.PARTIAL
                        : MasteryEvidenceResult.WRONG;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rate", rate);
        metadata.put("rateSource", usableScore(row) ? "SCORE" : "QUESTION_COUNT");
        return new MasteryEvidence(MasteryEvidenceType.EXAM, row.getBusinessId(), row.getEventTime(), classification,
                studentId, knowledgeId, rate, metadata);
    }

    private MasteryEvidence direct(MasteryEvidenceRow row, MasteryEvidenceType type, Long studentId, Long knowledgeId) {
        MasteryEvidenceResult result;
        try {
            result = MasteryEvidenceResult.valueOf(row.getResult());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("unsupported mastery evidence result: " + row.getResult(), ex);
        }
        return new MasteryEvidence(type, row.getBusinessId(), row.getEventTime(), result, studentId, knowledgeId,
                null, Map.of());
    }

    private BigDecimal rate(MasteryEvidenceRow row) {
        if (usableScore(row)) return row.getScore().divide(row.getFullScore(), 8, RoundingMode.HALF_UP);
        if (row.getCorrectCount() != null && row.getQuestionCount() != null && row.getQuestionCount() > 0) {
            return BigDecimal.valueOf(row.getCorrectCount()).divide(BigDecimal.valueOf(row.getQuestionCount()), 8,
                    RoundingMode.HALF_UP);
        }
        return null;
    }

    private boolean usableScore(MasteryEvidenceRow row) {
        return row.getScore() != null && row.getFullScore() != null && row.getFullScore().signum() > 0;
    }

    public record AggregatedEvidence(List<MasteryEvidence> items, LocalDateTime nextReviewTime) { }
}
