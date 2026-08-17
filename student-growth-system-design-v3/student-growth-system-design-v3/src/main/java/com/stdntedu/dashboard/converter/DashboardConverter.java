package com.stdntedu.dashboard.converter;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.dashboard.mapper.DashboardMasteryRow;
import com.stdntedu.dashboard.mapper.DashboardResourceRow;
import com.stdntedu.dashboard.mapper.DashboardWrongQuestionRow;
import com.stdntedu.generated.model.ExamSummaryDto;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.MasterySummaryDto;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.ResourceSummaryDto;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.generated.model.StudyLogDto;
import com.stdntedu.generated.model.WrongQuestionSummaryDto;
import com.stdntedu.generated.model.WrongSource;
import com.stdntedu.generated.model.WrongStatus;
import com.stdntedu.resource.mapper.StudyLogRow;
import com.stdntedu.score.converter.ScoreConverter;
import com.stdntedu.score.entity.ExamEntity;
import org.springframework.stereotype.Component;

@Component
public class DashboardConverter {
    private final IdConverter ids;
    private final ScoreConverter scores;

    public DashboardConverter(IdConverter ids, ScoreConverter scores) {
        this.ids = ids;
        this.scores = scores;
    }

    public ExamSummaryDto toExam(ExamEntity exam) {
        if (exam == null) {
            return null;
        }
        return new ExamSummaryDto()
                .id(ids.toString(exam.getId()))
                .examName(exam.getExamName())
                .examType(ExamType.fromValue(exam.getExamType()))
                .examDate(exam.getExamDate())
                .totalScore(exam.getTotalScore())
                .totalFullScore(exam.getTotalFullScore())
                .scoreRate(scores.rate(exam.getTotalScore(), exam.getTotalFullScore()));
    }

    public MasterySummaryDto toMastery(DashboardMasteryRow row, ZoneId databaseZone, ZoneId dashboardZone) {
        return new MasterySummaryDto()
                .knowledgeId(ids.toString(row.getKnowledgeId()))
                .knowledgeCode(row.getKnowledgeCode())
                .knowledgeName(row.getKnowledgeName())
                .subjectId(ids.toString(row.getSubjectId()))
                .subjectName(row.getSubjectName())
                .masteryScore(row.getMasteryScore())
                .evidenceCount(row.getEvidenceCount())
                .nextReviewTime(toOffset(row.getNextReviewTime(), databaseZone, dashboardZone));
    }

    public WrongQuestionSummaryDto toWrongQuestion(DashboardWrongQuestionRow row, ZoneId databaseZone,
            ZoneId dashboardZone) {
        return new WrongQuestionSummaryDto()
                .id(ids.toString(row.getId()))
                .subjectId(ids.toString(row.getSubjectId()))
                .subjectName(row.getSubjectName())
                .sourceType(WrongSource.fromValue(row.getSourceType()))
                .questionType(row.getQuestionType())
                .questionText(row.getQuestionText())
                .status(WrongStatus.fromValue(row.getStatus()))
                .reviewStage(Integer.toString(row.getReviewStage()))
                .nextReviewTime(toOffset(row.getNextReviewTime(), databaseZone, dashboardZone));
    }

    public ResourceSummaryDto toResource(DashboardResourceRow row, ZoneId databaseZone, ZoneId dashboardZone) {
        return new ResourceSummaryDto()
                .id(ids.toString(row.getResourceId()))
                .title(row.getTitle())
                .resourceType(row.getResourceType())
                .sourceType(row.getSourceType())
                .status(ResourceStatus.fromValue(row.getResourceStatus()))
                .studentStatus(StudentResourceStatus.fromValue(row.getStudentStatus()))
                .assignmentId(ids.toString(row.getAssignmentId()))
                .assignedTime(toOffset(row.getAssignedTime(), databaseZone, dashboardZone))
                .durationSeconds(row.getDurationSeconds())
                .latestProgressPercent(row.getLatestProgressPercent());
    }

    public StudyLogDto toStudyLog(StudyLogRow row, ZoneId databaseZone, ZoneId dashboardZone) {
        return new StudyLogDto()
                .id(ids.toString(row.getId()))
                .studentId(ids.toString(row.getStudentId()))
                .subjectId(ids.toString(row.getSubjectId()))
                .subjectName(row.getSubjectName())
                .studyDate(row.getStudyDate())
                .durationSeconds(row.getDurationSeconds())
                .content(row.getContent())
                .remark(row.getRemark())
                .version(row.getVersion())
                .createdAt(toOffset(row.getCreateTime(), databaseZone, dashboardZone))
                .updatedAt(toOffset(row.getUpdateTime(), databaseZone, dashboardZone));
    }

    private java.time.OffsetDateTime toOffset(LocalDateTime value, ZoneId databaseZone, ZoneId dashboardZone) {
        return value == null ? null : value.atZone(databaseZone).withZoneSameInstant(dashboardZone).toOffsetDateTime();
    }
}
