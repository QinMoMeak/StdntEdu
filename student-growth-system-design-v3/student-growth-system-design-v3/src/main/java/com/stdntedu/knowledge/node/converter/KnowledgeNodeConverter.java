package com.stdntedu.knowledge.node.converter;

import java.time.ZoneId;
import java.util.ArrayList;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.KnowledgeTreeNodeDto;
import com.stdntedu.knowledge.node.entity.KnowledgeNodeEntity;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeNodeConverter {
    private final IdConverter ids;

    public KnowledgeNodeConverter(IdConverter ids) {
        this.ids = ids;
    }

    public KnowledgeTreeNodeDto toDto(KnowledgeNodeEntity node, ZoneId zone) {
        return new KnowledgeTreeNodeDto()
                .id(ids.toString(node.getId()))
                .parentId(ids.toString(node.getParentId()))
                .stageId(ids.toString(node.getStageId()))
                .gradeId(ids.toString(node.getGradeId()))
                .subjectId(ids.toString(node.getSubjectId()))
                .nodeCode(node.getNodeCode())
                .name(node.getName())
                .nodeType(node.getNodeType())
                .levelNo(node.getLevelNo())
                .difficulty(node.getDifficulty())
                .description(node.getDescription())
                .keywords(node.getKeywords())
                .enabled(node.getEnabled())
                .sortOrder(node.getSortOrder())
                .version(node.getVersion())
                .createdAt(node.getCreateTime().atZone(zone).toOffsetDateTime())
                .updatedAt(node.getUpdateTime().atZone(zone).toOffsetDateTime())
                .children(new ArrayList<>());
    }
}
