package com.stdntedu.knowledge.mastery.algorithm;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;

import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceResult;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceType;

public record MasteryAlgorithmConfig(
        String algorithmVersion,
        BigDecimal correctMinimum,
        BigDecimal partialMinimum,
        boolean timeDecayEnabled,
        ZoneId timezone,
        Map<MasteryEvidenceResult, BigDecimal> initialScores,
        Map<String, BigDecimal> changes,
        BigDecimal maxIncreasePerEvent,
        BigDecimal maxDecreasePerEvent,
        BigDecimal maxDailyIncrease,
        BigDecimal maxDailyDecrease,
        Map<String, String> snapshot) {

    public BigDecimal initial(MasteryEvidenceResult result) {
        return required(initialScores.get(result), "initial " + result);
    }

    public BigDecimal change(MasteryEvidenceType type, MasteryEvidenceResult result) {
        return required(changes.get(type.name() + "." + result.name()), "change " + type + "." + result);
    }

    private BigDecimal required(BigDecimal value, String name) {
        if (value == null) throw new IllegalStateException("unsupported mastery evidence configuration: " + name);
        return value;
    }
}
