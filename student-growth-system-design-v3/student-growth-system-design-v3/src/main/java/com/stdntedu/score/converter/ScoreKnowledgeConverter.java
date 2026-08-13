package com.stdntedu.score.converter;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ScoreKnowledgeDto;
import com.stdntedu.generated.model.ScoreKnowledgeInput;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.entity.ScoreKnowledgeEntity;
import org.springframework.stereotype.Component;

@Component
public class ScoreKnowledgeConverter {
    private final IdConverter ids;
    private final ScoreConverter scores;

    public ScoreKnowledgeConverter(IdConverter ids, ScoreConverter scores) {
        this.ids = ids;
        this.scores = scores;
    }

    public ScoreKnowledgeEntity fromInput(ScoreKnowledgeInput input, Long scoreRecordId) {
        ScoreKnowledgeEntity entity = new ScoreKnowledgeEntity();
        entity.setScoreRecordId(scoreRecordId);
        entity.setKnowledgeId(ids.toLong(input.getKnowledgeId()));
        entity.setScore(input.getScore());
        entity.setFullScore(input.getFullScore());
        entity.setQuestionCount(input.getQuestionCount());
        entity.setCorrectCount(input.getCorrectCount());
        return entity;
    }

    public ScoreKnowledgeDto toDto(ScoreKnowledgeEntity entity, KnowledgeNodeReferenceEntity knowledge) {
        return new ScoreKnowledgeDto().knowledgeId(ids.toString(entity.getKnowledgeId())).knowledgeCode(knowledge.getNodeCode())
                .knowledgeName(knowledge.getName()).score(entity.getScore()).fullScore(entity.getFullScore())
                .scoreRate(scores.rate(entity.getScore(), entity.getFullScore())).questionCount(entity.getQuestionCount())
                .correctCount(entity.getCorrectCount()).correctRate(rate(entity.getCorrectCount(), entity.getQuestionCount()));
    }

    private java.math.BigDecimal rate(Integer numerator, Integer denominator) {
        if (numerator == null || denominator == null || denominator <= 0) return null;
        return java.math.BigDecimal.valueOf(numerator).divide(java.math.BigDecimal.valueOf(denominator), 4,
                java.math.RoundingMode.HALF_UP);
    }
}
