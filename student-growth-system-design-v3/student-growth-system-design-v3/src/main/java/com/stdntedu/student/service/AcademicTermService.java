package com.stdntedu.student.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.DataConflictException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermDto;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.student.converter.AcademicTermConverter;
import com.stdntedu.student.entity.AcademicTermEntity;
import com.stdntedu.student.entity.StudentEntity;
import com.stdntedu.student.mapper.AcademicTermMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicTermService {
    private final AcademicTermMapper terms;
    private final StudentMapper students;
    private final StageMapper stages;
    private final GradeMapper grades;
    private final AcademicTermConverter converter;
    private final IdConverter ids;

    public AcademicTermService(AcademicTermMapper terms, StudentMapper students, StageMapper stages, GradeMapper grades,
            AcademicTermConverter converter, IdConverter ids) {
        this.terms = terms;
        this.students = students;
        this.stages = stages;
        this.grades = grades;
        this.converter = converter;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public List<AcademicTermDto> list(String studentId, boolean currentOnly) {
        var query = Wrappers.<AcademicTermEntity>lambdaQuery()
                .eq(AcademicTermEntity::getStudentId, ids.toLong(studentId));
        if (currentOnly) query.eq(AcademicTermEntity::getIsCurrent, true);
        return terms.selectList(query.orderByDesc(AcademicTermEntity::getStartDate)
                .orderByDesc(AcademicTermEntity::getCreateTime)).stream().map(converter::toDto).toList();
    }

    @Transactional
    public AcademicTermDto create(AcademicTermCreateRequest request) {
        Long studentId = ids.toLong(request.getStudentId());
        if (Boolean.TRUE.equals(request.getCurrent())) {
            lockStudent(studentId);
        } else {
            requireStudent(studentId);
        }
        validateAcademicTerm(request.getStageId(), request.getGradeId(), request.getStartDate(), request.getEndDate());
        ensureNotDuplicate(studentId, request.getAcademicYear(), request.getSemester().getValue(), null);
        AcademicTermEntity entity = converter.fromCreate(request);
        entity.setDeleted(false);
        entity.setVersion(0);
        if (entity.getIsCurrent()) closeOtherCurrent(studentId, null);
        try {
            terms.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new DataConflictException("academic term already exists");
        }
        return converter.toDto(entity);
    }

    @Transactional
    public AcademicTermDto update(String termId, AcademicTermUpdateRequest request) {
        Long id = ids.toLong(termId);
        AcademicTermEntity entity = terms.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("academic term not found");
        if (!Objects.equals(entity.getVersion(), request.getVersion())) {
            throw new BusinessException("DATA_VERSION_CONFLICT", "academic term version conflict", HttpStatus.CONFLICT);
        }
        if (Boolean.TRUE.equals(request.getCurrent())) lockStudent(entity.getStudentId());
        validateAcademicTerm(request.getStageId(), request.getGradeId(), request.getStartDate(), request.getEndDate());
        ensureNotDuplicate(entity.getStudentId(), request.getAcademicYear(), request.getSemester().getValue(), id);
        converter.applyUpdate(request, entity);
        if (entity.getIsCurrent()) closeOtherCurrent(entity.getStudentId(), id);
        if (terms.updateById(entity) == 0) {
            throw new BusinessException("DATA_VERSION_CONFLICT", "academic term version conflict", HttpStatus.CONFLICT);
        }
        return converter.toDto(terms.selectById(id));
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void lockStudent(Long id) {
        if (students.selectIdForUpdate(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void validateAcademicTerm(String stageId, String gradeId, LocalDate startDate, LocalDate endDate) {
        StageEntity stage = stages.selectOne(Wrappers.<StageEntity>lambdaQuery()
                .eq(StageEntity::getId, ids.toLong(stageId)).eq(StageEntity::getEnabled, true));
        GradeEntity grade = grades.selectOne(Wrappers.<GradeEntity>lambdaQuery()
                .eq(GradeEntity::getId, ids.toLong(gradeId)).eq(GradeEntity::getEnabled, true));
        if (stage == null || grade == null || !Objects.equals(stage.getId(), grade.getStageId())) {
            throw new BusinessException("VALIDATION_ERROR", "grade does not belong to stage", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("VALIDATION_ERROR", "endDate cannot be before startDate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void ensureNotDuplicate(Long studentId, String year, String semester, Long excludeId) {
        var query = Wrappers.<AcademicTermEntity>lambdaQuery().eq(AcademicTermEntity::getStudentId, studentId)
                .eq(AcademicTermEntity::getAcademicYear, year).eq(AcademicTermEntity::getSemester, semester);
        if (excludeId != null) query.ne(AcademicTermEntity::getId, excludeId);
        if (terms.selectCount(query) > 0) throw new DataConflictException("academic term already exists");
    }

    private void closeOtherCurrent(Long studentId, Long excludeId) {
        var update = Wrappers.<AcademicTermEntity>lambdaUpdate().eq(AcademicTermEntity::getStudentId, studentId)
                .eq(AcademicTermEntity::getIsCurrent, true).set(AcademicTermEntity::getIsCurrent, false);
        if (excludeId != null) update.ne(AcademicTermEntity::getId, excludeId);
        terms.update(null, update);
    }
}
