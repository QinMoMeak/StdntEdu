package com.stdntedu.student.converter;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermDto;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.generated.model.SemesterType;
import com.stdntedu.student.entity.AcademicTermEntity;
import org.springframework.stereotype.Component;

@Component
public class AcademicTermConverter {
    private final IdConverter ids;

    public AcademicTermConverter(IdConverter ids) { this.ids = ids; }

    public AcademicTermEntity fromCreate(AcademicTermCreateRequest request) {
        AcademicTermEntity e = new AcademicTermEntity();
        e.setStudentId(ids.toLong(request.getStudentId()));
        e.setAcademicYear(request.getAcademicYear());
        e.setSemester(request.getSemester().getValue());
        e.setStageId(ids.toLong(request.getStageId()));
        e.setGradeId(ids.toLong(request.getGradeId()));
        e.setStartDate(request.getStartDate());
        e.setEndDate(request.getEndDate());
        e.setIsCurrent(Boolean.TRUE.equals(request.getCurrent()));
        return e;
    }

    public void applyUpdate(AcademicTermUpdateRequest request, AcademicTermEntity e) {
        e.setAcademicYear(request.getAcademicYear());
        e.setSemester(request.getSemester().getValue());
        e.setStageId(ids.toLong(request.getStageId()));
        e.setGradeId(ids.toLong(request.getGradeId()));
        e.setStartDate(request.getStartDate());
        e.setEndDate(request.getEndDate());
        e.setIsCurrent(Boolean.TRUE.equals(request.getCurrent()));
        e.setVersion(request.getVersion());
    }

    public AcademicTermDto toDto(AcademicTermEntity e) {
        return new AcademicTermDto().id(ids.toString(e.getId())).studentId(ids.toString(e.getStudentId()))
                .academicYear(e.getAcademicYear()).semester(SemesterType.fromValue(e.getSemester()))
                .stageId(ids.toString(e.getStageId())).gradeId(ids.toString(e.getGradeId()))
                .startDate(e.getStartDate()).endDate(e.getEndDate()).current(e.getIsCurrent())
                .version(e.getVersion()).createdAt(e.getCreateTime() == null ? null : e.getCreateTime().atOffset(java.time.ZoneOffset.ofHours(8)))
                .updatedAt(e.getUpdateTime() == null ? null : e.getUpdateTime().atOffset(java.time.ZoneOffset.ofHours(8)));
    }
}
