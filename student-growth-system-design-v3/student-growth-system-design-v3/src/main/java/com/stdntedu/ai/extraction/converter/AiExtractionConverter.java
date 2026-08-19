package com.stdntedu.ai.extraction.converter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionKnowledgeEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiExtractionQuestionDto;
import com.stdntedu.generated.model.AiExtractionQuestionStatus;
import com.stdntedu.generated.model.AiInputType;
import com.stdntedu.generated.model.AiKnowledgeCandidateDto;
import com.stdntedu.generated.model.AiTask;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.WrongSource;
import org.springframework.stereotype.Component;

@Component
public class AiExtractionConverter {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final IdConverter ids;

    public AiExtractionConverter(IdConverter ids) { this.ids = ids; }

    public AiTask task(AiExtractionTaskEntity entity, int fileCount, int questionCount) {
        return new AiTask().taskId(ids.toString(entity.getId())).studentId(ids.toString(entity.getStudentId()))
                .subjectId(ids.toString(entity.getSubjectId())).gradeId(ids.toString(entity.getGradeId()))
                .sourceType(WrongSource.fromValue(entity.getSourceType())).sourceName(entity.getSourceName())
                .examId(ids.toString(entity.getExamId())).modelId(ids.toString(entity.getModelId()))
                .status(AiTaskStatus.fromValue(entity.getStatus())).progressStage(entity.getProgressStage())
                .progressPercent(entity.getProgressPercent()).inputType(AiInputType.fromValue(entity.getInputType()))
                .fileCount(fileCount).questionCount(questionCount).warningCount(0)
                .retryCount(entity.getRetryCount()).maxRetryCount(entity.getMaxRetryCount())
                .errorCode(entity.getErrorCode()).errorMessage(entity.getErrorMessage())
                .startedAt(offset(entity.getStartedTime())).finishedAt(offset(entity.getFinishedTime()))
                .expiresAt(offset(entity.getExpireTime())).createdAt(offset(entity.getCreateTime()));
    }

    public AiExtractionQuestionDto question(AiExtractionQuestionEntity entity,
            List<AiExtractionQuestionKnowledgeEntity> candidates) {
        return new AiExtractionQuestionDto(entity.getId().toString(), entity.getTaskId().toString(),
                entity.getSequenceNo(), entity.getQuestionText(),
                AiExtractionQuestionStatus.fromValue(entity.getStatus()), Boolean.TRUE.equals(entity.getUserModified()),
                candidates.stream().map(this::candidate).toList(), List.of(), entity.getVersion(),
                offset(entity.getCreateTime()), offset(entity.getUpdateTime()))
                .pageNumber(entity.getPageNo()).questionNumber(entity.getQuestionNo())
                .questionType(entity.getQuestionType()).studentAnswer(entity.getStudentAnswer())
                .correctAnswer(entity.getCorrectAnswer()).answerSource(entity.getAnswerSource())
                .analysisText(entity.getAnalysisText()).analysisSource(entity.getAnalysisSource())
                .errorType(entity.getErrorType()).difficulty(entity.getDifficulty()).confidence(entity.getConfidence());
    }

    private AiKnowledgeCandidateDto candidate(AiExtractionQuestionKnowledgeEntity entity) {
        return new AiKnowledgeCandidateDto(entity.getKnowledgeName(), Boolean.TRUE.equals(entity.getIsPrimary()),
                Boolean.TRUE.equals(entity.getConfirmed()),
                AiKnowledgeCandidateDto.SourceEnum.fromValue(entity.getSource()))
                .knowledgeId(ids.toString(entity.getKnowledgeId())).knowledgeCode(entity.getKnowledgeCode())
                .confidence(entity.getConfidence());
    }

    private java.time.OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }
}
