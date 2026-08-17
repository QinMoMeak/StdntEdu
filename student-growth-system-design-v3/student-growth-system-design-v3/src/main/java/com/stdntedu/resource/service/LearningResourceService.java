package com.stdntedu.resource.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.InlineObject30AllOfData;
import com.stdntedu.generated.model.Resource;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.ResourceUpdate;
import com.stdntedu.resource.converter.LearningResourceConverter;
import com.stdntedu.resource.entity.LearningResourceEntity;
import com.stdntedu.resource.entity.LearningResourceKnowledgeEntity;
import com.stdntedu.resource.entity.ResourceKnowledgeNodeEntity;
import com.stdntedu.resource.mapper.LearningResourceKnowledgeMapper;
import com.stdntedu.resource.mapper.LearningResourceMapper;
import com.stdntedu.resource.mapper.ResourceKnowledgeNodeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningResourceService {
    private final LearningResourceMapper resources;
    private final LearningResourceKnowledgeMapper resourceKnowledge;
    private final ResourceKnowledgeNodeMapper knowledgeNodes;
    private final SubjectMapper subjects;
    private final LearningResourceConverter converter;
    private final IdConverter ids;

    public LearningResourceService(LearningResourceMapper resources,
            LearningResourceKnowledgeMapper resourceKnowledge, ResourceKnowledgeNodeMapper knowledgeNodes,
            SubjectMapper subjects, LearningResourceConverter converter, IdConverter ids) {
        this.resources = resources;
        this.resourceKnowledge = resourceKnowledge;
        this.knowledgeNodes = knowledgeNodes;
        this.subjects = subjects;
        this.converter = converter;
        this.ids = ids;
    }

    @Transactional
    public Resource create(ResourceCreate request) {
        Normalized normalized = validate(request.getTitle(), request.getResourceType(), request.getSourceType(),
                request.getSourceUrl(), request.getSubjectId(), request.getDurationSeconds(), request.getDifficulty(),
                request.getStatus(), request.getDescription(), request.getTags(), request.getKnowledgeIds(),
                request.getCoverAttachmentId());
        request.setTitle(normalized.title());
        request.setResourceType(normalized.resourceType());
        request.setSourceType(normalized.sourceType());
        request.setSourceUrl(normalized.sourceUrl());
        request.setDescription(normalized.description());
        LearningResourceEntity entity = converter.fromCreate(request, nextCode(), normalized.tagsJson());
        resources.insert(entity);
        replaceKnowledge(entity.getId(), normalized.knowledgeIds());
        return getById(entity.getId());
    }

    @Transactional(readOnly = true)
    public Resource get(String resourceId) {
        return getById(ids.toLong(resourceId));
    }

    @Transactional(readOnly = true)
    public InlineObject30AllOfData list(int page, int pageSize) {
        Page<LearningResourceEntity> result = resources.selectPage(new Page<>(page, pageSize),
                Wrappers.<LearningResourceEntity>lambdaQuery().orderByDesc(LearningResourceEntity::getCreateTime)
                        .orderByDesc(LearningResourceEntity::getId));
        Map<Long, List<Long>> knowledge = knowledgeByResource(result.getRecords().stream()
                .map(LearningResourceEntity::getId).toList());
        List<Resource> items = result.getRecords().stream()
                .map(entity -> converter.toDto(entity, knowledge.getOrDefault(entity.getId(), List.of()))).toList();
        return new InlineObject30AllOfData().page(page).pageSize(pageSize).total(result.getTotal())
                .totalPages(totalPages(result.getTotal(), pageSize)).items(items);
    }

    @Transactional
    public Resource update(String resourceId, ResourceUpdate request) {
        Long id = ids.toLong(resourceId);
        LearningResourceEntity entity = require(id);
        if (!entity.getVersion().equals(request.getVersion())) throw versionConflict();
        Normalized normalized = validate(request.getTitle(), request.getResourceType(), request.getSourceType(),
                request.getSourceUrl(), request.getSubjectId(), request.getDurationSeconds(), request.getDifficulty(),
                request.getStatus(), request.getDescription(), request.getTags(), request.getKnowledgeIds(),
                request.getCoverAttachmentId());
        request.setTitle(normalized.title());
        request.setResourceType(normalized.resourceType());
        request.setSourceType(normalized.sourceType());
        request.setSourceUrl(normalized.sourceUrl());
        request.setDescription(normalized.description());
        converter.applyUpdate(request, normalized.tagsJson(), entity);
        if (resources.updateById(entity) == 0) throw versionConflict();
        replaceKnowledge(id, normalized.knowledgeIds());
        return getById(id);
    }

    private Normalized validate(String title, String resourceType, String sourceType, String sourceUrl,
            String subjectId, Integer durationSeconds, Integer difficulty, ResourceStatus status, String description,
            List<String> tags, List<String> knowledgeIds, String coverAttachmentId) {
        if (coverAttachmentId != null) {
            throw new BusinessException("BUSINESS_RULE_VIOLATION",
                    "non-null coverAttachmentId is not supported in the current version",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String cleanTitle = required(title, "title", 255);
        String cleanResourceType = required(resourceType, "resourceType", 32);
        String cleanSourceType = required(sourceType, "sourceType", 32);
        String cleanSourceUrl = optional(sourceUrl);
        String cleanDescription = optional(description);
        if (durationSeconds != null && durationSeconds < 0) throw invalid("durationSeconds must be non-negative");
        if (difficulty != null && (difficulty < 0 || difficulty > 5)) {
            throw invalid("difficulty must be an integer from 0 to 5");
        }
        if (status == null) throw invalid("status is required");
        Long subject = subjectId == null ? null : ids.toLong(subjectId);
        if (subject != null) requireEnabledSubject(subject);
        List<String> cleanTags = tags == null ? List.of() : tags.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        String tagsJson = converter.writeTags(cleanTags);
        if (tagsJson.length() > 512) throw invalid("serialized tags exceed 512 characters");
        List<Long> nodes = new ArrayList<>(new LinkedHashSet<>(ids.toLongs(knowledgeIds)));
        validateKnowledge(nodes, subject);
        return new Normalized(cleanTitle, cleanResourceType, cleanSourceType, cleanSourceUrl, cleanDescription,
                tagsJson, nodes);
    }

    private void validateKnowledge(List<Long> nodeIds, Long subjectId) {
        if (nodeIds.isEmpty()) return;
        List<ResourceKnowledgeNodeEntity> nodes = knowledgeNodes.selectBatchIds(nodeIds);
        Map<Long, ResourceKnowledgeNodeEntity> byId = nodes.stream()
                .collect(Collectors.toMap(ResourceKnowledgeNodeEntity::getId, node -> node));
        for (Long nodeId : nodeIds) {
            ResourceKnowledgeNodeEntity node = byId.get(nodeId);
            if (node == null || !Boolean.TRUE.equals(node.getEnabled())) {
                throw invalid("knowledge node does not exist or is disabled: " + nodeId);
            }
            if (subjectId != null && !subjectId.equals(node.getSubjectId())) {
                throw invalid("knowledge node does not belong to resource subject: " + nodeId);
            }
        }
    }

    private void replaceKnowledge(Long resourceId, List<Long> nodeIds) {
        resourceKnowledge.deleteByResourceId(resourceId);
        List<LearningResourceKnowledgeEntity> relations = nodeIds.stream().map(nodeId -> {
            LearningResourceKnowledgeEntity relation = new LearningResourceKnowledgeEntity();
            relation.setResourceId(resourceId);
            relation.setKnowledgeId(nodeId);
            return relation;
        }).toList();
        if (!relations.isEmpty()) resourceKnowledge.insertBatch(relations);
    }

    private Resource getById(Long id) {
        LearningResourceEntity entity = require(id);
        List<Long> nodeIds = resourceKnowledge.selectByResourceId(id).stream()
                .map(LearningResourceKnowledgeEntity::getKnowledgeId).toList();
        return converter.toDto(entity, nodeIds);
    }

    private Map<Long, List<Long>> knowledgeByResource(List<Long> resourceIds) {
        if (resourceIds.isEmpty()) return Map.of();
        return resourceKnowledge.selectByResourceIds(resourceIds).stream()
                .collect(Collectors.groupingBy(LearningResourceKnowledgeEntity::getResourceId,
                        Collectors.mapping(LearningResourceKnowledgeEntity::getKnowledgeId, Collectors.toList())));
    }

    private LearningResourceEntity require(Long id) {
        LearningResourceEntity entity = resources.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("resource not found");
        return entity;
    }

    private void requireEnabledSubject(Long id) {
        SubjectEntity subject = subjects.selectById(id);
        if (subject == null || !Boolean.TRUE.equals(subject.getEnabled())) {
            throw invalid("subject does not exist or is disabled");
        }
    }

    private String nextCode() {
        return "RES" + UUID.randomUUID().toString().replace("-", "").substring(0, 29).toUpperCase();
    }

    private String required(String value, String name, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw invalid(name + " is required");
        if (result.length() > maxLength) throw invalid(name + " exceeds " + maxLength + " characters");
        return result;
    }

    private String optional(String value) {
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

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "resource version conflict", HttpStatus.CONFLICT);
    }

    private record Normalized(String title, String resourceType, String sourceType, String sourceUrl,
            String description, String tagsJson, List<Long> knowledgeIds) { }
}
