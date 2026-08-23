package com.stdntedu.growth.event.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.stdntedu.attachment.service.AttachmentService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.OptimisticLockException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.GrowthEventCreateRequest;
import com.stdntedu.generated.model.GrowthEventDto;
import com.stdntedu.generated.model.GrowthEventPageResponseAllOfData;
import com.stdntedu.generated.model.GrowthEventUpdateRequest;
import com.stdntedu.growth.event.converter.GrowthEventConverter;
import com.stdntedu.growth.event.entity.GrowthEventEntity;
import com.stdntedu.growth.event.mapper.GrowthEventAttachmentMapper;
import com.stdntedu.growth.event.mapper.GrowthEventMapper;
import com.stdntedu.growth.event.projection.GrowthEventAttachmentRow;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthEventService {
    static final String ENTITY_TYPE = "GROWTH_EVENT";
    static final String ATTACHMENT_ROLE = "ATTACHMENT";

    private final GrowthEventMapper events;
    private final GrowthEventAttachmentMapper relations;
    private final StudentMapper students;
    private final AttachmentService attachments;
    private final GrowthEventConverter converter;
    private final IdConverter ids;

    public GrowthEventService(GrowthEventMapper events, GrowthEventAttachmentMapper relations,
            StudentMapper students, AttachmentService attachments, GrowthEventConverter converter,
            IdConverter ids) {
        this.events = events;
        this.relations = relations;
        this.students = students;
        this.attachments = attachments;
        this.converter = converter;
        this.ids = ids;
    }

    @Transactional
    public GrowthEventDto create(GrowthEventCreateRequest request) {
        Normalized value = validate(request.getStudentId(), request.getEventType(), request.getTitle(),
                request.getEventDate(), request.getDescription(), request.getTags(), request.getAttachmentIds());
        GrowthEventEntity event = entity(value);
        event.setDeleted(false);
        event.setVersion(0);
        events.insert(event);
        replaceAttachments(event.getId(), value.attachmentIds());
        return getById(event.getId());
    }

    @Transactional(readOnly = true)
    public GrowthEventDto get(String eventId) {
        return getById(ids.toLong(eventId));
    }

    @Transactional(readOnly = true)
    public GrowthEventPageResponseAllOfData list(String studentId, String eventType, LocalDate startDate,
            LocalDate endDate, String keyword, Integer page, Integer pageSize) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw rule("startDate must not be after endDate");
        }
        int pageNo = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        String type = optional(eventType);
        String search = optional(keyword);
        long total = events.countPage(student, type, startDate, endDate, search);
        List<GrowthEventEntity> rows = total == 0 ? List.of()
                : events.selectPage(student, type, startDate, endDate, search, (long) (pageNo - 1) * size, size);
        Map<Long, List<GrowthEventAttachmentRow>> grouped = attachmentRows(rows.stream()
                .map(GrowthEventEntity::getId).toList());
        List<GrowthEventDto> items = rows.stream()
                .map(row -> converter.toDto(row, grouped.getOrDefault(row.getId(), List.of()))).toList();
        return new GrowthEventPageResponseAllOfData().page(pageNo).pageSize(size).total(total)
                .totalPages((int) ((total + size - 1) / size)).items(items);
    }

    @Transactional
    public GrowthEventDto update(String eventId, GrowthEventUpdateRequest request) {
        Long id = ids.toLong(eventId);
        GrowthEventEntity current = require(id);
        requireVersion(current, request.getVersion());
        Normalized value = validate(request.getStudentId(), request.getEventType(), request.getTitle(),
                request.getEventDate(), request.getDescription(), request.getTags(), request.getAttachmentIds());
        GrowthEventEntity replacement = entity(value);
        replacement.setId(id);
        if (events.updateWithVersion(replacement, request.getVersion()) == 0) throwAfterWriteFailure(id);
        replaceAttachments(id, value.attachmentIds());
        return getById(id);
    }

    @Transactional
    public void delete(String eventId, Integer version) {
        Long id = ids.toLong(eventId);
        GrowthEventEntity current = require(id);
        requireVersion(current, version);
        if (events.deleteWithVersion(id, version) == 0) throwAfterWriteFailure(id);
        relations.deleteByEventId(ENTITY_TYPE, id);
    }

    private Normalized validate(String studentId, String eventType, String title, LocalDate eventDate,
            String description, List<String> tags, List<String> attachmentIds) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        String type = required(eventType, "eventType", 32);
        if (events.selectEnabledEventTypeLabel(type) == null) throw rule("growth event type does not exist or is disabled");
        String cleanTitle = required(title, "title", 255);
        if (eventDate == null) throw invalid("eventDate is required");
        String cleanDescription = optional(description);
        List<String> cleanTags = tags == null ? List.of() : tags.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        String tagsJson = converter.writeTags(cleanTags);
        if (tagsJson.length() > 512) throw invalid("serialized tags exceed 512 characters");
        List<Long> attachmentKeys = ids.toLongs(attachmentIds);
        if (new LinkedHashSet<>(attachmentKeys).size() != attachmentKeys.size()) {
            throw rule("attachmentIds must not contain duplicates");
        }
        attachments.validateForAssociation(attachmentKeys);
        return new Normalized(student, type, cleanTitle, eventDate, cleanDescription, tagsJson, attachmentKeys);
    }

    private GrowthEventEntity entity(Normalized value) {
        GrowthEventEntity event = new GrowthEventEntity();
        event.setStudentId(value.studentId());
        event.setEventType(value.eventType());
        event.setTitle(value.title());
        event.setEventDate(value.eventDate());
        event.setDescription(value.description());
        event.setTags(value.tagsJson());
        return event;
    }

    private GrowthEventDto getById(Long id) {
        GrowthEventEntity event = events.selectViewById(id);
        if (event == null) throw notFound();
        return converter.toDto(event, relations.selectByEventIds(ENTITY_TYPE, ATTACHMENT_ROLE, List.of(id)));
    }

    private void replaceAttachments(Long eventId, List<Long> attachmentIds) {
        relations.deleteByEventId(ENTITY_TYPE, eventId);
        if (!attachmentIds.isEmpty()) relations.insertBatch(ENTITY_TYPE, eventId, ATTACHMENT_ROLE, attachmentIds);
    }

    private Map<Long, List<GrowthEventAttachmentRow>> attachmentRows(List<Long> eventIds) {
        Map<Long, List<GrowthEventAttachmentRow>> grouped = new HashMap<>();
        if (eventIds.isEmpty()) return grouped;
        for (GrowthEventAttachmentRow row : relations.selectByEventIds(ENTITY_TYPE, ATTACHMENT_ROLE, eventIds)) {
            grouped.computeIfAbsent(row.getEventId(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private GrowthEventEntity require(Long id) {
        GrowthEventEntity event = events.selectById(id);
        if (event == null) throw notFound();
        return event;
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void requireVersion(GrowthEventEntity event, Integer version) {
        if (version == null || !version.equals(event.getVersion())) {
            throw new OptimisticLockException("growth event version conflict");
        }
    }

    private void throwAfterWriteFailure(Long id) {
        if (events.selectById(id) == null) throw notFound();
        throw new OptimisticLockException("growth event version conflict");
    }

    private String required(String value, String field, int max) {
        String clean = optional(value);
        if (clean == null) throw invalid(field + " is required");
        if (clean.length() > max) throw invalid(field + " exceeds " + max + " characters");
        return clean;
    }

    private String optional(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("growth event not found");
    }

    private record Normalized(Long studentId, String eventType, String title, LocalDate eventDate,
            String description, String tagsJson, List<Long> attachmentIds) { }
}
