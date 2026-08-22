package com.stdntedu.resource.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.StudentResourceCreateRequest;
import com.stdntedu.generated.model.StudentResourceDto;
import com.stdntedu.generated.model.StudentResourcePageResponseAllOfData;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.generated.model.StudentResourceUpdateRequest;
import com.stdntedu.resource.converter.StudentResourceConverter;
import com.stdntedu.resource.entity.LearningResourceEntity;
import com.stdntedu.resource.entity.StudentResourceAssignmentEntity;
import com.stdntedu.resource.mapper.LearningResourceMapper;
import com.stdntedu.resource.mapper.StudentResourceAssignmentMapper;
import com.stdntedu.resource.mapper.StudentResourceQueryMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentResourceService {
    private final StudentResourceAssignmentMapper assignments;
    private final StudentResourceQueryMapper queries;
    private final StudentMapper students;
    private final LearningResourceMapper resources;
    private final StudentResourceConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public StudentResourceService(StudentResourceAssignmentMapper assignments, StudentResourceQueryMapper queries,
            StudentMapper students, LearningResourceMapper resources, StudentResourceConverter converter,
            SystemTimezoneProvider timezone, IdConverter ids) {
        this.assignments = assignments;
        this.queries = queries;
        this.students = students;
        this.resources = resources;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional
    public StudentResourceDto create(StudentResourceCreateRequest request) {
        Long studentId = ids.toLong(request.getStudentId());
        Long resourceId = ids.toLong(request.getResourceId());
        requireStudent(studentId);
        requireResource(resourceId);
        if (assignments.selectCount(Wrappers.<StudentResourceAssignmentEntity>lambdaQuery()
                .eq(StudentResourceAssignmentEntity::getStudentId, studentId)
                .eq(StudentResourceAssignmentEntity::getResourceId, resourceId)) > 0) {
            throw duplicate();
        }
        StudentResourceAssignmentEntity entity = new StudentResourceAssignmentEntity();
        entity.setStudentId(studentId);
        entity.setResourceId(resourceId);
        entity.setStatus(request.getStatus() == null ? StudentResourceStatus.WAITING : request.getStatus());
        entity.setAssignedTime(timezone.localDateTime());
        entity.setRemark(request.getRemark());
        entity.setVersion(0);
        try {
            assignments.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw duplicate();
        }
        return getById(entity.getId());
    }

    @Transactional(readOnly = true)
    public StudentResourceDto get(String assignmentId) {
        return getById(ids.toLong(assignmentId));
    }

    @Transactional(readOnly = true)
    public StudentResourcePageResponseAllOfData list(String studentId, StudentResourceStatus status,
            String subjectId, int page, int pageSize) {
        Long studentKey = ids.toLong(studentId);
        Long subjectKey = subjectId == null ? null : ids.toLong(subjectId);
        requireStudent(studentKey);
        long total = queries.count(studentKey, status, subjectKey);
        ZoneId zone = timezone.get();
        List<StudentResourceDto> items = queries.selectPage(studentKey, status, subjectKey,
                (long) (page - 1) * pageSize, pageSize).stream().map(row -> converter.toDto(row, zone)).toList();
        return new StudentResourcePageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    @Transactional
    public StudentResourceDto update(String assignmentId, StudentResourceUpdateRequest request) {
        Long id = ids.toLong(assignmentId);
        if (assignments.selectById(id) == null) throw notFound();
        if (assignments.updateWithVersion(id, request.getStatus(), request.getRemark(), request.getVersion()) == 0) {
            if (assignments.selectById(id) == null) throw notFound();
            throw versionConflict();
        }
        return getById(id);
    }

    private StudentResourceDto getById(Long assignmentId) {
        var row = queries.selectDetail(assignmentId);
        if (row == null) throw notFound();
        return converter.toDto(row, timezone.get());
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private LearningResourceEntity requireResource(Long id) {
        LearningResourceEntity resource = resources.selectById(id);
        if (resource == null) throw new ResourceNotFoundException("resource not found");
        return resource;
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("student resource assignment not found");
    }

    private BusinessException duplicate() {
        return new BusinessException("DUPLICATE_DATA", "student resource assignment already exists",
                HttpStatus.CONFLICT);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "student resource assignment version conflict",
                HttpStatus.CONFLICT);
    }
}
