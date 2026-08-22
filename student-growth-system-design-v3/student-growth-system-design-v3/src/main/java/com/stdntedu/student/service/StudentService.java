package com.stdntedu.student.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.DataConflictException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Student;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentUpdate;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.student.converter.StudentConverter;
import com.stdntedu.student.entity.StudentEntity;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final StudentMapper students;
    private final StageMapper stages;
    private final GradeMapper grades;
    private final StudentConverter converter;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public StudentService(StudentMapper students, StageMapper stages, GradeMapper grades, StudentConverter converter,
            IdConverter ids, SystemTimezoneProvider time) {
        this.students = students;
        this.stages = stages;
        this.grades = grades;
        this.converter = converter;
        this.ids = ids;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<Student> list() {
        return students.selectList(Wrappers.<StudentEntity>lambdaQuery()
                .orderByDesc(StudentEntity::getId)).stream().map(converter::toDto).toList();
    }

    @Transactional
    public Student create(StudentCreate request) {
        String name = normalizedName(request.getName());
        validateBirthday(request.getBirthday());
        validateStageGrade(request.getCurrentStageId(), request.getCurrentGradeId());
        StudentEntity entity = converter.fromCreate(request);
        entity.setName(name);
        entity.setStudentCode(generateStudentCode());
        entity.setDeleted(false);
        entity.setVersion(0);
        try {
            students.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new DataConflictException("student code already exists");
        }
        return converter.toDto(entity);
    }

    @Transactional(readOnly = true)
    public Student get(String studentId) {
        StudentEntity entity = students.selectById(ids.toLong(studentId));
        if (entity == null) throw new ResourceNotFoundException("student not found");
        return converter.toDto(entity);
    }

    @Transactional
    public Student update(String studentId, StudentUpdate request) {
        Long id = ids.toLong(studentId);
        StudentEntity entity = students.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("student not found");
        if (!java.util.Objects.equals(entity.getVersion(), request.getVersion())) {
            throw new BusinessException("DATA_VERSION_CONFLICT", "student version conflict", HttpStatus.CONFLICT);
        }
        validateBirthday(request.getBirthday());
        validateStageGrade(request.getCurrentStageId(), request.getCurrentGradeId());
        converter.applyUpdate(request, entity);
        entity.setName(normalizedName(request.getName()));
        if (students.updateById(entity) == 0) {
            throw new BusinessException("DATA_VERSION_CONFLICT", "student version conflict", HttpStatus.CONFLICT);
        }
        return get(studentId);
    }

    private void validateStageGrade(String stageId, String gradeId) {
        StageEntity stage = stages.selectOne(Wrappers.<StageEntity>lambdaQuery()
                .eq(StageEntity::getId, ids.toLong(stageId)).eq(StageEntity::getEnabled, true));
        GradeEntity grade = grades.selectOne(Wrappers.<GradeEntity>lambdaQuery()
                .eq(GradeEntity::getId, ids.toLong(gradeId)).eq(GradeEntity::getEnabled, true));
        if (stage == null || grade == null || !java.util.Objects.equals(grade.getStageId(), stage.getId())) {
            throw new BusinessException("VALIDATION_ERROR", "grade does not belong to stage", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String normalizedName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) throw new BusinessException("VALIDATION_ERROR", "name is required", HttpStatus.UNPROCESSABLE_ENTITY);
        return normalized;
    }

    private void validateBirthday(LocalDate birthday) {
        if (birthday != null && birthday.isAfter(time.today())) {
            throw new BusinessException("VALIDATION_ERROR", "birthday cannot be in the future", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String generateStudentCode() {
        String suffix = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "STU" + time.localDateTime().format(CODE_DATE) + suffix;
    }
}
