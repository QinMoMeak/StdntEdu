package com.stdntedu.base.service;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.base.converter.BaseDataConverter;
import com.stdntedu.base.entity.DictItemEntity;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.DictItemMapper;
import com.stdntedu.base.mapper.DictTypeMapper;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.DictionaryItemDto;
import com.stdntedu.generated.model.GradeDto;
import com.stdntedu.generated.model.StageDto;
import com.stdntedu.generated.model.SubjectDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BaseDataService {
    private final StageMapper stages;
    private final GradeMapper grades;
    private final SubjectMapper subjects;
    private final DictTypeMapper dictTypes;
    private final DictItemMapper dictItems;
    private final BaseDataConverter converter;
    private final IdConverter ids;

    public BaseDataService(StageMapper stages, GradeMapper grades, SubjectMapper subjects, DictTypeMapper dictTypes,
            DictItemMapper dictItems, BaseDataConverter converter, IdConverter ids) {
        this.stages = stages;
        this.grades = grades;
        this.subjects = subjects;
        this.dictTypes = dictTypes;
        this.dictItems = dictItems;
        this.converter = converter;
        this.ids = ids;
    }

    public List<StageDto> listStages(String stageId, boolean enabledOnly) {
        var query = Wrappers.<StageEntity>lambdaQuery();
        if (enabledOnly) query.eq(StageEntity::getEnabled, true);
        if (stageId != null) query.eq(StageEntity::getId, ids.toLong(stageId));
        return stages.selectList(query.orderByAsc(StageEntity::getSortOrder).orderByAsc(StageEntity::getId))
                .stream().map(converter::toDto).toList();
    }

    public List<GradeDto> listGrades(String stageId, boolean enabledOnly) {
        var query = Wrappers.<GradeEntity>lambdaQuery();
        if (enabledOnly) query.eq(GradeEntity::getEnabled, true);
        if (stageId != null) query.eq(GradeEntity::getStageId, ids.toLong(stageId));
        return grades.selectList(query.orderByAsc(GradeEntity::getSortOrder).orderByAsc(GradeEntity::getId))
                .stream().map(converter::toDto).toList();
    }

    public List<SubjectDto> listSubjects(boolean enabledOnly) {
        var query = Wrappers.<SubjectEntity>lambdaQuery();
        if (enabledOnly) query.eq(SubjectEntity::getEnabled, true);
        return subjects.selectList(query.orderByAsc(SubjectEntity::getSortOrder).orderByAsc(SubjectEntity::getId))
                .stream().map(converter::toDto).toList();
    }

    public List<DictionaryItemDto> listDictionaryItems(String dictCode, boolean enabledOnly) {
        var type = dictTypes.selectOne(Wrappers.<com.stdntedu.base.entity.DictTypeEntity>lambdaQuery()
                .eq(com.stdntedu.base.entity.DictTypeEntity::getDictCode, dictCode)
                .eq(com.stdntedu.base.entity.DictTypeEntity::getEnabled, true));
        if (type == null) throw new ResourceNotFoundException("dictionary type not found");
        var query = Wrappers.<DictItemEntity>lambdaQuery().eq(DictItemEntity::getDictTypeId, type.getId());
        if (enabledOnly) query.eq(DictItemEntity::getEnabled, true);
        return dictItems.selectList(query.orderByAsc(DictItemEntity::getSortOrder).orderByAsc(DictItemEntity::getId))
                .stream().map(item -> converter.toDto(item, dictCode)).toList();
    }
}
