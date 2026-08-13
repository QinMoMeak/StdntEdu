package com.stdntedu.score.converter;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ScoreListItemDto;
import com.stdntedu.generated.model.ScoreTrendPointDto;
import com.stdntedu.generated.model.SubjectScore;
import com.stdntedu.generated.model.SubjectScoreDto;
import com.stdntedu.score.entity.ExamEntity;
import com.stdntedu.score.entity.ScoreRecordEntity;
import com.stdntedu.score.mapper.ScoreListRow;
import org.springframework.stereotype.Component;

@Component
public class ScoreConverter {
    private final IdConverter ids;

    public ScoreConverter(IdConverter ids) { this.ids = ids; }

    public ScoreRecordEntity fromInput(SubjectScore input, Long examId, Long studentId) {
        ScoreRecordEntity entity = new ScoreRecordEntity();
        entity.setExamId(examId);
        entity.setStudentId(studentId);
        apply(input, entity);
        return entity;
    }

    public void apply(SubjectScore input, ScoreRecordEntity entity) {
        entity.setSubjectId(ids.toLong(input.getSubjectId()));
        entity.setScore(input.getScore());
        entity.setFullScore(input.getFullScore());
        entity.setClassRank(input.getClassRank());
        entity.setGradeRank(input.getGradeRank());
        entity.setClassSize(input.getClassSize());
        entity.setGradeSize(input.getGradeSize());
    }

    public SubjectScoreDto toSubjectDto(ScoreRecordEntity entity) {
        return new SubjectScoreDto().subjectId(ids.toString(entity.getSubjectId())).score(entity.getScore())
                .fullScore(entity.getFullScore()).classRank(entity.getClassRank()).gradeRank(entity.getGradeRank())
                .classSize(entity.getClassSize()).gradeSize(entity.getGradeSize());
    }

    public ScoreListItemDto toListItem(ScoreRecordEntity score, ExamEntity exam, String subjectName) {
        return new ScoreListItemDto().id(ids.toString(score.getId())).examId(ids.toString(exam.getId()))
                .examName(exam.getExamName()).examType(com.stdntedu.generated.model.ExamType.fromValue(exam.getExamType()))
                .examDate(exam.getExamDate()).academicTermId(ids.toString(exam.getAcademicTermId()))
                .subjectId(ids.toString(score.getSubjectId())).subjectName(subjectName).score(score.getScore())
                .fullScore(score.getFullScore()).scoreRate(rate(score.getScore(), score.getFullScore()))
                .classRank(score.getClassRank()).gradeRank(score.getGradeRank());
    }

    public ScoreListItemDto toListItem(ScoreListRow score) {
        return new ScoreListItemDto().id(ids.toString(score.getId())).examId(ids.toString(score.getExamId()))
                .examName(score.getExamName()).examType(com.stdntedu.generated.model.ExamType.fromValue(score.getExamType()))
                .examDate(score.getExamDate()).academicTermId(ids.toString(score.getAcademicTermId()))
                .subjectId(ids.toString(score.getSubjectId())).subjectName(score.getSubjectName()).score(score.getScore())
                .fullScore(score.getFullScore()).scoreRate(rate(score.getScore(), score.getFullScore()))
                .classRank(score.getClassRank()).gradeRank(score.getGradeRank());
    }

    public ScoreTrendPointDto toTrendPoint(ExamEntity exam, ScoreRecordEntity score) {
        BigDecimal numerator = score == null ? exam.getTotalScore() : score.getScore();
        BigDecimal denominator = score == null ? exam.getTotalFullScore() : score.getFullScore();
        return new ScoreTrendPointDto().examId(ids.toString(exam.getId())).examName(exam.getExamName())
                .examDate(exam.getExamDate()).score(numerator).fullScore(denominator).scoreRate(rate(numerator, denominator))
                .classRank(score == null ? null : score.getClassRank()).gradeRank(score == null ? null : score.getGradeRank());
    }

    public BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return null;
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
