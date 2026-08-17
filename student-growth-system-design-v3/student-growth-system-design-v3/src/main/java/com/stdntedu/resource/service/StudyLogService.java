package com.stdntedu.resource.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.StudyLogCreateRequest;
import com.stdntedu.generated.model.StudyLogDto;
import com.stdntedu.generated.model.StudyLogPageResponseAllOfData;
import com.stdntedu.generated.model.StudyLogUpdateRequest;
import com.stdntedu.resource.converter.StudyLogConverter;
import com.stdntedu.resource.entity.StudyLogEntity;
import com.stdntedu.resource.mapper.StudyLogMapper;
import com.stdntedu.resource.mapper.StudyLogQueryMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyLogService {
    private static final int TEXT_MAX_LENGTH = 65_535;
    private final StudyLogMapper logs;
    private final StudyLogQueryMapper queries;
    private final StudentMapper students;
    private final SubjectMapper subjects;
    private final StudyLogConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public StudyLogService(StudyLogMapper logs, StudyLogQueryMapper queries, StudentMapper students,
            SubjectMapper subjects, StudyLogConverter converter, SystemTimezoneProvider timezone, IdConverter ids) {
        this.logs = logs;
        this.queries = queries;
        this.students = students;
        this.subjects = subjects;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional
    public StudyLogDto create(StudyLogCreateRequest request) {
        validate(request.getStudentId(), request.getSubjectId(), request.getStudyDate(), request.getDurationSeconds(),
                request.getContent(), request.getRemark());
        StudyLogEntity entity = converter.fromCreate(request);
        logs.insert(entity);
        return getById(entity.getId());
    }

    @Transactional(readOnly = true)
    public StudyLogDto get(String studyLogId) {
        return getById(ids.toLong(studyLogId));
    }

    @Transactional(readOnly = true)
    public StudyLogPageResponseAllOfData list(String studentId, String subjectId, LocalDate startDate,
            LocalDate endDate, String keyword, int page, int pageSize) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw invalid("endDate cannot be before startDate");
        }
        Long subject = subjectId == null ? null : ids.toLong(subjectId);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        long total = queries.count(student, subject, startDate, endDate, normalizedKeyword);
        ZoneId zone = timezone.get();
        List<StudyLogDto> items = queries.selectPage(student, subject, startDate, endDate, normalizedKeyword,
                (long) (page - 1) * pageSize, pageSize).stream().map(row -> converter.toDto(row, zone)).toList();
        return new StudyLogPageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    @Transactional
    public StudyLogDto update(String studyLogId, StudyLogUpdateRequest request) {
        Long id = ids.toLong(studyLogId);
        StudyLogEntity entity = require(id);
        if (!entity.getVersion().equals(request.getVersion())) throw versionConflict();
        validate(request.getStudentId(), request.getSubjectId(), request.getStudyDate(), request.getDurationSeconds(),
                request.getContent(), request.getRemark());
        converter.applyUpdate(request, entity);
        if (logs.updateById(entity) == 0) throw versionConflict();
        return getById(id);
    }

    @Transactional
    public void delete(String studyLogId) {
        Long id = ids.toLong(studyLogId);
        require(id);
        if (logs.deleteById(id) == 0) throw new ResourceNotFoundException("study log not found");
    }

    private void validate(String studentId, String subjectId, LocalDate studyDate, Integer durationSeconds,
            String content, String remark) {
        requireStudent(ids.toLong(studentId));
        if (subjectId != null) requireEnabledSubject(ids.toLong(subjectId));
        if (studyDate == null) throw invalid("studyDate is required");
        if (studyDate.isAfter(LocalDate.now(timezone.get()))) throw invalid("studyDate cannot be in the future");
        if (durationSeconds == null || durationSeconds < 0) {
            throw invalid("durationSeconds must be non-negative");
        }
        if (content != null && content.length() > TEXT_MAX_LENGTH) throw invalid("content is too long");
        if (remark != null && remark.length() > TEXT_MAX_LENGTH) throw invalid("remark is too long");
    }

    private StudyLogDto getById(Long id) {
        StudyLogEntity entity = require(id);
        SubjectEntity subject = entity.getSubjectId() == null ? null : subjects.selectById(entity.getSubjectId());
        return converter.toDto(entity, subject == null ? null : subject.getName(), timezone.get());
    }

    private StudyLogEntity require(Long id) {
        StudyLogEntity entity = logs.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("study log not found");
        return entity;
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void requireEnabledSubject(Long id) {
        SubjectEntity subject = subjects.selectById(id);
        if (subject == null || !Boolean.TRUE.equals(subject.getEnabled())) {
            throw invalid("subject does not exist or is disabled");
        }
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "study log version conflict", HttpStatus.CONFLICT);
    }
}
