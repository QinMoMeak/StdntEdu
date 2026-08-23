package com.stdntedu.knowledge.node.service;

import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.KnowledgeNodeCreateRequest;
import com.stdntedu.generated.model.KnowledgeNodeDisableRequest;
import com.stdntedu.generated.model.KnowledgeNodeMoveRequest;
import com.stdntedu.generated.model.KnowledgeNodeUpdateRequest;
import com.stdntedu.generated.model.KnowledgeTreeNodeDto;
import com.stdntedu.knowledge.node.converter.KnowledgeNodeConverter;
import com.stdntedu.knowledge.node.entity.KnowledgeNodeEntity;
import com.stdntedu.knowledge.node.mapper.KnowledgeNodeMapper;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeNodeService {
    private final KnowledgeNodeMapper nodes;
    private final StageMapper stages;
    private final GradeMapper grades;
    private final SubjectMapper subjects;
    private final KnowledgeNodeConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public KnowledgeNodeService(KnowledgeNodeMapper nodes, StageMapper stages, GradeMapper grades,
            SubjectMapper subjects, KnowledgeNodeConverter converter, SystemTimezoneProvider timezone,
            IdConverter ids) {
        this.nodes = nodes;
        this.stages = stages;
        this.grades = grades;
        this.subjects = subjects;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeTreeNodeDto> tree(String stageId, String gradeId, String subjectId, boolean enabledOnly) {
        Long stage = optionalId(stageId);
        Long grade = optionalId(gradeId);
        Long subject = optionalId(subjectId);
        validateFilterScope(stage, grade, subject);
        List<KnowledgeNodeEntity> rows = nodes.selectTreeRows(stage, grade, subject, enabledOnly);
        ZoneId zone = timezone.get();
        Map<Long, KnowledgeTreeNodeDto> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.getId(), converter.toDto(row, zone)));

        List<KnowledgeTreeNodeDto> roots = new ArrayList<>();
        for (KnowledgeNodeEntity row : rows) {
            KnowledgeTreeNodeDto dto = byId.get(row.getId());
            if (row.getParentId() == null) {
                roots.add(dto);
            } else {
                KnowledgeTreeNodeDto parent = byId.get(row.getParentId());
                if (parent != null) parent.getChildren().add(dto);
            }
        }
        return roots;
    }

    @Transactional
    public KnowledgeTreeNodeDto create(KnowledgeNodeCreateRequest request) {
        Scope scope = validateScope(request.getStageId(), request.getGradeId(), request.getSubjectId());
        Long parentId = optionalId(request.getParentId());
        KnowledgeNodeEntity parent = parentId == null ? null : requireEnabledParent(parentId);
        requireCompatible(parent, scope);

        KnowledgeNodeEntity node = new KnowledgeNodeEntity();
        node.setParentId(parentId);
        node.setNodeCode(required(request.getNodeCode(), "nodeCode", 64));
        node.setName(required(request.getName(), "name", 128));
        node.setNodeType(required(request.getNodeType(), "nodeType", 32));
        applyScope(node, scope);
        node.setLevelNo(parent == null ? 1 : parent.getLevelNo() + 1);
        node.setSortOrder(sortOrder(request.getSortOrder()));
        node.setDifficulty(difficulty(request.getDifficulty()));
        node.setDescription(request.getDescription());
        node.setKeywords(optional(request.getKeywords(), 512, "keywords"));
        node.setEnabled(true);
        node.setDeleted(false);
        node.setVersion(1);
        nodes.insert(node);
        return dto(require(node.getId()));
    }

    @Transactional
    public KnowledgeTreeNodeDto update(String knowledgeId, KnowledgeNodeUpdateRequest request) {
        Long id = ids.toLong(knowledgeId);
        KnowledgeNodeEntity current = require(id);
        requireVersion(current, request.getVersion());
        Scope scope = validateScope(request.getStageId(), request.getGradeId(), request.getSubjectId());
        KnowledgeNodeEntity parent = current.getParentId() == null ? null : requireEnabledParent(current.getParentId());
        requireCompatible(parent, scope);
        for (KnowledgeNodeEntity child : directChildren(id)) requireCompatibleScope(scope, child);

        current.setNodeCode(required(request.getNodeCode(), "nodeCode", 64));
        current.setName(required(request.getName(), "name", 128));
        current.setNodeType(required(request.getNodeType(), "nodeType", 32));
        applyScope(current, scope);
        current.setSortOrder(sortOrder(request.getSortOrder()));
        current.setDifficulty(difficulty(request.getDifficulty()));
        current.setDescription(request.getDescription());
        current.setKeywords(optional(request.getKeywords(), 512, "keywords"));
        if (nodes.updateWithVersion(current) == 0) throwAfterWriteFailure(id);
        return dto(require(id));
    }

    @Transactional
    public KnowledgeTreeNodeDto move(String knowledgeId, KnowledgeNodeMoveRequest request) {
        Long id = ids.toLong(knowledgeId);
        KnowledgeNodeEntity current = require(id);
        requireVersion(current, request.getVersion());
        Long parentId = optionalId(request.getParentId());
        if (Objects.equals(id, parentId)) throw invalid("knowledge node cannot be its own parent");

        List<KnowledgeNodeEntity> all = allNodes();
        Set<Long> descendants = descendantsOf(id, all);
        if (parentId != null && descendants.contains(parentId)) {
            throw invalid("knowledge node cannot be moved below its descendant");
        }
        KnowledgeNodeEntity parent = parentId == null ? null : requireEnabledParent(parentId);
        requireCompatible(parent, Scope.of(current));
        int levelNo = parent == null ? 1 : parent.getLevelNo() + 1;
        int delta = levelNo - current.getLevelNo();
        if (nodes.moveWithVersion(id, parentId, levelNo, sortOrder(request.getSortOrder()), request.getVersion()) == 0) {
            throwAfterWriteFailure(id);
        }
        if (delta != 0 && !descendants.isEmpty()) {
            nodes.update(null, Wrappers.<KnowledgeNodeEntity>lambdaUpdate().in(KnowledgeNodeEntity::getId, descendants)
                    .setSql("level_no=level_no+" + delta));
        }
        return dto(require(id));
    }

    @Transactional
    public KnowledgeTreeNodeDto disable(String knowledgeId, KnowledgeNodeDisableRequest request) {
        Long id = ids.toLong(knowledgeId);
        KnowledgeNodeEntity current = require(id);
        requireVersion(current, request.getVersion());
        if (!Boolean.TRUE.equals(current.getEnabled())) throw invalid("knowledge node is already disabled");
        if (nodes.disableWithVersion(id, request.getVersion()) == 0) throwAfterWriteFailure(id);
        return dto(require(id));
    }

    private List<KnowledgeNodeEntity> allNodes() {
        return nodes.selectList(Wrappers.<KnowledgeNodeEntity>lambdaQuery()
                .orderByAsc(KnowledgeNodeEntity::getSortOrder).orderByAsc(KnowledgeNodeEntity::getId));
    }

    private List<KnowledgeNodeEntity> directChildren(Long id) {
        return nodes.selectList(Wrappers.<KnowledgeNodeEntity>lambdaQuery().eq(KnowledgeNodeEntity::getParentId, id));
    }

    private Set<Long> descendantsOf(Long id, List<KnowledgeNodeEntity> all) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (KnowledgeNodeEntity node : all) {
            if (node.getParentId() != null) {
                children.computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>()).add(node.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(children.getOrDefault(id, List.of()));
        while (!queue.isEmpty()) {
            Long child = queue.removeFirst();
            if (!result.add(child)) throw invalid("knowledge hierarchy contains a cycle");
            queue.addAll(children.getOrDefault(child, List.of()));
        }
        return result;
    }

    private Scope validateScope(String stageId, String gradeId, String subjectId) {
        Long stage = optionalId(stageId);
        Long grade = optionalId(gradeId);
        Long subject = ids.toLong(subjectId);
        StageEntity stageRow = stage == null ? null : stages.selectById(stage);
        GradeEntity gradeRow = grade == null ? null : grades.selectById(grade);
        SubjectEntity subjectRow = subjects.selectById(subject);
        if (stage != null && (stageRow == null || !Boolean.TRUE.equals(stageRow.getEnabled()))) {
            throw invalid("stage does not exist or is disabled");
        }
        if (grade != null && (gradeRow == null || !Boolean.TRUE.equals(gradeRow.getEnabled()))) {
            throw invalid("grade does not exist or is disabled");
        }
        if (subjectRow == null || !Boolean.TRUE.equals(subjectRow.getEnabled())) {
            throw invalid("subject does not exist or is disabled");
        }
        if (stage != null && gradeRow != null && !Objects.equals(stage, gradeRow.getStageId())) {
            throw invalid("grade does not belong to stage");
        }
        return new Scope(stage, grade, subject);
    }

    private void validateFilterScope(Long stage, Long grade, Long subject) {
        if (stage != null && stages.selectById(stage) == null) throw new ResourceNotFoundException("stage not found");
        GradeEntity gradeRow = grade == null ? null : grades.selectById(grade);
        if (grade != null && gradeRow == null) throw new ResourceNotFoundException("grade not found");
        if (subject != null && subjects.selectById(subject) == null) throw new ResourceNotFoundException("subject not found");
        if (stage != null && gradeRow != null && !Objects.equals(stage, gradeRow.getStageId())) {
            throw invalid("grade does not belong to stage");
        }
    }

    private void requireCompatible(KnowledgeNodeEntity parent, Scope child) {
        if (parent != null) requireCompatibleScope(Scope.of(parent), child);
    }

    private void requireCompatibleScope(Scope parent, KnowledgeNodeEntity child) {
        requireCompatibleScope(parent, Scope.of(child));
    }

    private void requireCompatibleScope(Scope parent, Scope child) {
        if ((parent.stageId() != null && !Objects.equals(parent.stageId(), child.stageId()))
                || (parent.gradeId() != null && !Objects.equals(parent.gradeId(), child.gradeId()))
                || (parent.subjectId() != null && !Objects.equals(parent.subjectId(), child.subjectId()))) {
            throw invalid("parent and child knowledge scopes are incompatible");
        }
    }

    private KnowledgeNodeEntity requireEnabledParent(Long id) {
        KnowledgeNodeEntity parent = require(id);
        if (!Boolean.TRUE.equals(parent.getEnabled())) throw invalid("parent knowledge node is disabled");
        return parent;
    }

    private KnowledgeNodeEntity require(Long id) {
        KnowledgeNodeEntity node = nodes.selectById(id);
        if (node == null) throw new ResourceNotFoundException("knowledge node not found");
        return node;
    }

    private KnowledgeTreeNodeDto dto(KnowledgeNodeEntity node) {
        return converter.toDto(node, timezone.get());
    }

    private void applyScope(KnowledgeNodeEntity node, Scope scope) {
        node.setStageId(scope.stageId());
        node.setGradeId(scope.gradeId());
        node.setSubjectId(scope.subjectId());
    }

    private void requireVersion(KnowledgeNodeEntity node, Integer version) {
        if (version == null || !version.equals(node.getVersion())) throw versionConflict();
    }

    private void throwAfterWriteFailure(Long id) {
        if (nodes.selectById(id) == null) throw new ResourceNotFoundException("knowledge node not found");
        throw versionConflict();
    }

    private Long optionalId(String value) {
        return value == null ? null : ids.toLong(value);
    }

    private int sortOrder(Integer value) {
        if (value == null) return 0;
        if (value < 0) throw invalid("sortOrder must be greater than or equal to zero");
        return value;
    }

    private Integer difficulty(Integer value) {
        if (value != null && (value < 1 || value > 5)) throw invalid("difficulty must be between 1 and 5");
        return value;
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw invalid(field + " is required");
        if (normalized.length() > maxLength) throw invalid(field + " is too long");
        return normalized;
    }

    private String optional(String value, int maxLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw invalid(field + " is too long");
        return normalized.isEmpty() ? null : normalized;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "knowledge node version conflict", HttpStatus.CONFLICT);
    }

    private record Scope(Long stageId, Long gradeId, Long subjectId) {
        static Scope of(KnowledgeNodeEntity node) {
            return new Scope(node.getStageId(), node.getGradeId(), node.getSubjectId());
        }
    }
}
