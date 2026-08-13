package com.stdntedu.student.converter;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Student;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentUpdate;
import com.stdntedu.student.entity.StudentEntity;
import org.springframework.stereotype.Component;

@Component
public class StudentConverter {
    private final IdConverter ids;

    public StudentConverter(IdConverter ids) { this.ids = ids; }

    public Student toDto(StudentEntity e) {
        return new Student().id(ids.toString(e.getId())).studentCode(e.getStudentCode()).name(e.getName())
                .birthday(e.getBirthday()).school(e.getSchool()).currentStageId(ids.toString(e.getCurrentStageId()))
                .currentGradeId(ids.toString(e.getCurrentGradeId())).remark(e.getRemark()).version(e.getVersion());
    }

    public StudentEntity fromCreate(StudentCreate request) {
        StudentEntity e = new StudentEntity();
        e.setName(request.getName());
        e.setBirthday(request.getBirthday());
        e.setSchool(request.getSchool());
        e.setCurrentStageId(ids.toLong(request.getCurrentStageId()));
        e.setCurrentGradeId(ids.toLong(request.getCurrentGradeId()));
        e.setRemark(request.getRemark());
        return e;
    }

    public void applyUpdate(StudentUpdate request, StudentEntity e) {
        e.setName(request.getName());
        e.setBirthday(request.getBirthday());
        e.setSchool(request.getSchool());
        e.setCurrentStageId(ids.toLong(request.getCurrentStageId()));
        e.setCurrentGradeId(ids.toLong(request.getCurrentGradeId()));
        e.setRemark(request.getRemark());
        e.setVersion(request.getVersion());
    }
}
