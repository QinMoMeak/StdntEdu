package com.stdntedu.resource.converter;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Resource;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.ResourceUpdate;
import com.stdntedu.resource.entity.LearningResourceEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LearningResourceConverter {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final IdConverter ids;
    private final ObjectMapper objectMapper;

    public LearningResourceConverter(IdConverter ids, ObjectMapper objectMapper) {
        this.ids = ids;
        this.objectMapper = objectMapper;
    }

    public LearningResourceEntity fromCreate(ResourceCreate request, String resourceCode, String tagsJson) {
        LearningResourceEntity entity = new LearningResourceEntity();
        entity.setResourceCode(resourceCode);
        apply(request.getTitle(), request.getResourceType(), request.getSourceType(), request.getSourceUrl(),
                request.getSubjectId(), request.getDurationSeconds(), request.getDifficulty(), request.getStatus(),
                request.getDescription(), tagsJson, entity);
        entity.setDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    public void applyUpdate(ResourceUpdate request, String tagsJson, LearningResourceEntity entity) {
        apply(request.getTitle(), request.getResourceType(), request.getSourceType(), request.getSourceUrl(),
                request.getSubjectId(), request.getDurationSeconds(), request.getDifficulty(), request.getStatus(),
                request.getDescription(), tagsJson, entity);
        entity.setVersion(request.getVersion());
    }

    public Resource toDto(LearningResourceEntity entity, List<Long> knowledgeIds) {
        Resource dto = new Resource();
        dto.setId(ids.toString(entity.getId()));
        dto.setResourceCode(entity.getResourceCode());
        dto.setTitle(entity.getTitle());
        dto.setResourceType(entity.getResourceType());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceUrl(entity.getSourceUrl());
        dto.setSubjectId(ids.toString(entity.getSubjectId()));
        dto.setDurationSeconds(entity.getDurationSeconds());
        dto.setDifficulty(entity.getDifficulty());
        dto.setStatus(ResourceStatus.fromValue(entity.getStatus()));
        dto.setDescription(entity.getDescription());
        dto.setTags(readTags(entity.getTags()));
        dto.setKnowledgeIds(knowledgeIds.stream().map(ids::toString).toList());
        dto.setCoverAttachmentId(null);
        dto.setVersion(entity.getVersion());
        return dto;
    }

    public String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw invalid("tags cannot be serialized");
        }
    }

    private List<String> readTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored resource tags are invalid JSON", ex);
        }
    }

    private void apply(String title, String resourceType, String sourceType, String sourceUrl, String subjectId,
            Integer durationSeconds, Integer difficulty, ResourceStatus status, String description, String tagsJson,
            LearningResourceEntity entity) {
        entity.setTitle(title);
        entity.setResourceType(resourceType);
        entity.setSourceType(sourceType);
        entity.setSourceUrl(sourceUrl);
        entity.setSubjectId(subjectId == null ? null : ids.toLong(subjectId));
        entity.setDurationSeconds(durationSeconds);
        entity.setDifficulty(difficulty);
        entity.setStatus(status.getValue());
        entity.setDescription(description);
        entity.setTags(tagsJson);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
