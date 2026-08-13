package com.stdntedu.knowledge.mastery.converter;

import java.time.ZoneId;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.MasteryDto;
import com.stdntedu.generated.model.MasteryHistoryDto;
import com.stdntedu.knowledge.mastery.entity.MasteryHistoryEntity;
import com.stdntedu.knowledge.mastery.entity.StudentMasteryEntity;
import org.springframework.stereotype.Component;

@Component
public class MasteryConverter {
    private final IdConverter ids;

    public MasteryConverter(IdConverter ids) {
        this.ids = ids;
    }

    public MasteryDto toDto(StudentMasteryEntity entity, ZoneId zone) {
        return new MasteryDto().id(ids.toString(entity.getId())).studentId(ids.toString(entity.getStudentId()))
                .knowledgeId(ids.toString(entity.getKnowledgeId())).score(entity.getMasteryScore())
                .locked(entity.getManualLocked()).version(entity.getVersion())
                .updatedAt(entity.getUpdateTime() == null ? null : entity.getUpdateTime().atZone(zone).toOffsetDateTime());
    }

    public MasteryHistoryDto toDto(MasteryHistoryEntity entity, ZoneId zone) {
        return new MasteryHistoryDto().id(ids.toString(entity.getId())).studentId(ids.toString(entity.getStudentId()))
                .knowledgeId(ids.toString(entity.getKnowledgeId())).score(entity.getScoreAfter())
                .source(entity.getBusinessType() == null ? entity.getEventType() : entity.getBusinessType())
                .reason(entity.getRemark())
                .createdAt(entity.getCreateTime() == null ? null : entity.getCreateTime().atZone(zone).toOffsetDateTime());
    }
}
