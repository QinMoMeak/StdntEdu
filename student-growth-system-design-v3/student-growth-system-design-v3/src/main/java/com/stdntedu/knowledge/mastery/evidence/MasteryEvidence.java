package com.stdntedu.knowledge.mastery.evidence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

public record MasteryEvidence(
        MasteryEvidenceType evidenceType,
        Long businessId,
        LocalDateTime eventTime,
        MasteryEvidenceResult result,
        Long studentId,
        Long knowledgeId,
        BigDecimal scoreRate,
        Map<String, Object> sourceMetadata) {

    public static final Comparator<MasteryEvidence> ORDER = Comparator
            .comparing(MasteryEvidence::eventTime)
            .thenComparingInt(evidence -> evidence.evidenceType().priority())
            .thenComparing(MasteryEvidence::businessId);

    public MasteryEvidence {
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }
}
