package com.stdntedu.growth.event.converter;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Attachment;
import com.stdntedu.generated.model.GrowthEventDto;
import com.stdntedu.growth.event.entity.GrowthEventEntity;
import com.stdntedu.growth.event.projection.GrowthEventAttachmentRow;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GrowthEventConverter {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final IdConverter ids;
    private final ObjectMapper json;
    private final SystemTimezoneProvider time;

    public GrowthEventConverter(IdConverter ids, ObjectMapper json, SystemTimezoneProvider time) {
        this.ids = ids;
        this.json = json;
        this.time = time;
    }

    public GrowthEventDto toDto(GrowthEventEntity event, List<GrowthEventAttachmentRow> rows) {
        List<Attachment> attachments = rows.stream().map(this::toAttachment).toList();
        return new GrowthEventDto()
                .id(ids.toString(event.getId()))
                .studentId(ids.toString(event.getStudentId()))
                .eventType(event.getEventType())
                .eventTypeLabel(event.getEventTypeLabel() == null ? event.getEventType() : event.getEventTypeLabel())
                .title(event.getTitle())
                .eventDate(event.getEventDate())
                .description(event.getDescription())
                .tags(readTags(event.getTags()))
                .attachmentIds(attachments.stream().map(Attachment::getId).toList())
                .attachments(attachments)
                .version(event.getVersion())
                .createdAt(time.toOffsetDateTime(event.getCreateTime()))
                .updatedAt(time.toOffsetDateTime(event.getUpdateTime()));
    }

    public String writeTags(List<String> tags) {
        try {
            return json.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw invalid("tags cannot be serialized");
        }
    }

    private List<String> readTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored growth event tags are invalid JSON", ex);
        }
    }

    private Attachment toAttachment(GrowthEventAttachmentRow row) {
        String id = ids.toString(row.getAttachmentId());
        return new Attachment(id, row.getFileName(), row.getMimeType(), row.getFileSize(), row.getSha256(),
                "/api/v1/attachments/" + id + "/content", time.toOffsetDateTime(row.getCreateTime()));
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
