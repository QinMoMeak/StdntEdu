package com.stdntedu.score.converter;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Exam;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.score.entity.ExamEntity;
import org.springframework.stereotype.Component;

@Component
public class ExamConverter {
    private final IdConverter ids;

    public ExamConverter(IdConverter ids) { this.ids = ids; }

    public ExamEntity fromCreate(ExamCreate request) {
        ExamEntity entity = new ExamEntity();
        apply(request.getStudentId(), request.getAcademicTermId(), request.getExamName(), request.getExamType().getValue(),
                request.getExamDate(), entity);
        return entity;
    }

    public void applyUpdate(ExamUpdate request, ExamEntity entity) {
        apply(request.getStudentId(), request.getAcademicTermId(), request.getExamName(), request.getExamType().getValue(),
                request.getExamDate(), entity);
    }

    public Exam toDto(ExamEntity entity) {
        return new Exam().id(ids.toString(entity.getId())).studentId(ids.toString(entity.getStudentId()))
                .academicTermId(ids.toString(entity.getAcademicTermId())).examName(entity.getExamName())
                .examType(com.stdntedu.generated.model.ExamType.fromValue(entity.getExamType()))
                .examDate(entity.getExamDate()).version(entity.getVersion());
    }

    private void apply(String studentId, String academicTermId, String examName, String examType,
            java.time.LocalDate examDate, ExamEntity entity) {
        entity.setStudentId(ids.toLong(studentId));
        entity.setAcademicTermId(academicTermId == null ? null : ids.toLong(academicTermId));
        entity.setExamName(examName == null ? null : examName.trim());
        entity.setExamType(examType);
        entity.setExamDate(examDate);
    }
}
