package com.stdntedu.knowledge.mastery.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.stdntedu.knowledge.mastery.evidence.MasteryEvidence;
import org.springframework.stereotype.Component;

@Component
public class MasteryCalculator {
    static final BigDecimal ZERO = new BigDecimal("0.00");
    static final BigDecimal HUNDRED = new BigDecimal("100.00");

    public BigDecimal initial(MasteryEvidence evidence, MasteryAlgorithmConfig config) {
        return normalize(config.initial(evidence.result()));
    }

    public BigDecimal eventLimitedChange(MasteryEvidence evidence, MasteryAlgorithmConfig config) {
        BigDecimal raw = config.change(evidence.evidenceType(), evidence.result());
        if (raw.signum() > 0) return raw.min(config.maxIncreasePerEvent());
        if (raw.signum() < 0) return raw.max(config.maxDecreasePerEvent().negate());
        return ZERO;
    }

    public BigDecimal apply(BigDecimal score, BigDecimal change) {
        return normalize(score.add(change));
    }

    public BigDecimal normalize(BigDecimal value) {
        if (value == null) return null;
        return value.max(ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
}
