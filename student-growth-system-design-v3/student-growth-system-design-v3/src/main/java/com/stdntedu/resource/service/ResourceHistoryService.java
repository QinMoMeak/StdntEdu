package com.stdntedu.resource.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ResourceHistoryCreateRequest;
import com.stdntedu.generated.model.ResourceHistoryDto;
import com.stdntedu.generated.model.ResourceHistoryPageResponseAllOfData;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.resource.converter.ResourceHistoryConverter;
import com.stdntedu.resource.entity.LearningResourceEntity;
import com.stdntedu.resource.entity.ResourceHistoryEntity;
import com.stdntedu.resource.entity.StudentResourceAssignmentEntity;
import com.stdntedu.resource.mapper.LearningResourceMapper;
import com.stdntedu.resource.mapper.ResourceHistoryMapper;
import com.stdntedu.resource.mapper.ResourceHistoryQueryMapper;
import com.stdntedu.resource.mapper.StudentResourceAssignmentMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceHistoryService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final ResourceHistoryMapper histories;
    private final ResourceHistoryQueryMapper queries;
    private final LearningResourceMapper resources;
    private final StudentResourceAssignmentMapper assignments;
    private final StudentMapper students;
    private final ResourceHistoryConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public ResourceHistoryService(ResourceHistoryMapper histories, ResourceHistoryQueryMapper queries,
            LearningResourceMapper resources, StudentResourceAssignmentMapper assignments, StudentMapper students,
            ResourceHistoryConverter converter, SystemTimezoneProvider timezone, IdConverter ids) {
        this.histories = histories;
        this.queries = queries;
        this.resources = resources;
        this.assignments = assignments;
        this.students = students;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional
    public ResourceHistoryDto create(String resourceId, ResourceHistoryCreateRequest request) {
        Long resourceKey = ids.toLong(resourceId);
        Long studentKey = ids.toLong(request.getStudentId());
        LearningResourceEntity resource = requireResource(resourceKey);
        requireStudent(studentKey);
        validate(request);
        ZoneId zone = timezone.get();
        ResourceHistoryEntity entity = converter.fromCreate(resourceKey, studentKey, request, zone);
        histories.insert(entity);
        transitionAssignment(studentKey, resourceKey, request.getProgressPercent(), request.getCompleted());
        entity = histories.selectById(entity.getId());
        return converter.toDto(entity, resource, zone);
    }

    private void transitionAssignment(Long studentId, Long resourceId, BigDecimal progress, Boolean completed) {
        StudentResourceAssignmentEntity assignment = assignments.selectOne(
                Wrappers.<StudentResourceAssignmentEntity>lambdaQuery()
                        .eq(StudentResourceAssignmentEntity::getStudentId, studentId)
                        .eq(StudentResourceAssignmentEntity::getResourceId, resourceId));
        if (assignment == null || assignment.getStatus() == StudentResourceStatus.REVIEW
                || assignment.getStatus() == StudentResourceStatus.ARCHIVED) return;
        StudentResourceStatus target = null;
        if (Boolean.TRUE.equals(completed) && assignment.getStatus() != StudentResourceStatus.COMPLETED) {
            target = StudentResourceStatus.COMPLETED;
        } else if (!Boolean.TRUE.equals(completed) && progress.compareTo(BigDecimal.ZERO) > 0
                && assignment.getStatus() == StudentResourceStatus.WAITING) {
            target = StudentResourceStatus.LEARNING;
        }
        if (target == null) return;
        if (assignments.transitionWithVersion(assignment.getId(), assignment.getVersion(), assignment.getStatus(),
                target) == 0) {
            // Re-read once to observe a concurrent user update; never force an automatic overwrite.
            assignments.selectById(assignment.getId());
        }
    }

    @Transactional(readOnly = true)
    public ResourceHistoryPageResponseAllOfData listForResource(String resourceId, String studentId, int page,
            int pageSize) {
        Long resource = ids.toLong(resourceId);
        Long student = ids.toLong(studentId);
        requireResource(resource);
        requireStudent(student);
        return query(student, resource, null, null, null, null, null, null, page, pageSize);
    }

    @Transactional(readOnly = true)
    public ResourceHistoryPageResponseAllOfData listForStudent(String studentId, String subjectId,
            String resourceType, String sourceType, Boolean completed, LocalDate startDate, LocalDate endDate,
            int page, int pageSize) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        validateDateRange(startDate, endDate);
        return query(student, null, subjectId == null ? null : ids.toLong(subjectId), clean(resourceType),
                clean(sourceType), completed, startDate, endDate, page, pageSize);
    }

    private ResourceHistoryPageResponseAllOfData query(Long studentId, Long resourceId, Long subjectId,
            String resourceType, String sourceType, Boolean completed, LocalDate startDate, LocalDate endDate,
            int page, int pageSize) {
        long total = queries.count(studentId, resourceId, subjectId, resourceType, sourceType, completed, startDate,
                endDate);
        ZoneId zone = timezone.get();
        List<ResourceHistoryDto> items = queries.selectPage(studentId, resourceId, subjectId, resourceType, sourceType,
                completed, startDate, endDate, (long) (page - 1) * pageSize, pageSize).stream()
                .map(row -> converter.toDto(row, zone)).toList();
        return new ResourceHistoryPageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    private void validate(ResourceHistoryCreateRequest request) {
        if (request.getDurationSeconds() == null || request.getDurationSeconds() < 0) {
            throw invalid("durationSeconds must be non-negative");
        }
        if (request.getProgressPercent() == null || request.getProgressPercent().compareTo(BigDecimal.ZERO) < 0
                || request.getProgressPercent().compareTo(ONE_HUNDRED) > 0) {
            throw invalid("progressPercent must be from 0 to 100");
        }
        if (request.getCompleted() == null) throw invalid("completed is required");
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw invalid("endTime cannot be before startTime");
        }
    }

    private LearningResourceEntity requireResource(Long id) {
        LearningResourceEntity entity = resources.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("resource not found");
        return entity;
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) throw invalid("endDate cannot be before startDate");
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
