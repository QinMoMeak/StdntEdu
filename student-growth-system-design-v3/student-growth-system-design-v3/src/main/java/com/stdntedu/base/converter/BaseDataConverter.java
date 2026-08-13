package com.stdntedu.base.converter;

import com.stdntedu.base.entity.DictItemEntity;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.DictionaryItemDto;
import com.stdntedu.generated.model.GradeDto;
import com.stdntedu.generated.model.StageDto;
import com.stdntedu.generated.model.SubjectDto;
import org.springframework.stereotype.Component;

@Component
public class BaseDataConverter {
    private final IdConverter ids;

    public BaseDataConverter(IdConverter ids) { this.ids = ids; }

    public StageDto toDto(StageEntity e) {
        return new StageDto().id(ids.toString(e.getId())).code(e.getCode()).name(e.getName()).enabled(e.getEnabled());
    }

    public GradeDto toDto(GradeEntity e) {
        return new GradeDto().id(ids.toString(e.getId())).stageId(ids.toString(e.getStageId()))
                .code(e.getCode()).name(e.getName()).enabled(e.getEnabled());
    }

    public SubjectDto toDto(SubjectEntity e) {
        return new SubjectDto().id(ids.toString(e.getId())).code(e.getCode()).name(e.getName()).enabled(e.getEnabled());
    }

    public DictionaryItemDto toDto(DictItemEntity e, String dictType) {
        return new DictionaryItemDto().id(ids.toString(e.getId())).dictType(dictType).code(e.getItemCode())
                .name(e.getItemLabel()).sortOrder(e.getSortOrder()).enabled(e.getEnabled());
    }
}
