package com.stdntedu.resource.converter;

import java.time.ZoneId;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.StudyLogCreateRequest;
import com.stdntedu.generated.model.StudyLogDto;
import com.stdntedu.generated.model.StudyLogUpdateRequest;
import com.stdntedu.resource.entity.StudyLogEntity;
import com.stdntedu.resource.mapper.StudyLogRow;
import org.springframework.stereotype.Component;

@Component
public class StudyLogConverter {
    private final IdConverter ids;

    public StudyLogConverter(IdConverter ids) {
        this.ids = ids;
    }

    public StudyLogEntity fromCreate(StudyLogCreateRequest request) {
        StudyLogEntity entity = new StudyLogEntity();
        apply(request.getStudentId(), request.getSubjectId(), request.getStudyDate(), request.getDurationSeconds(),
                request.getContent(), request.getRemark(), entity);
        entity.setDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    public void applyUpdate(StudyLogUpdateRequest request, StudyLogEntity entity) {
        apply(request.getStudentId(), request.getSubjectId(), request.getStudyDate(), request.getDurationSeconds(),
                request.getContent(), request.getRemark(), entity);
        entity.setVersion(request.getVersion());
    }

    public StudyLogDto toDto(StudyLogEntity entity, String subjectName, ZoneId zone) {
        return base(entity.getId(), entity.getStudentId(), entity.getSubjectId(), subjectName, entity.getStudyDate(),
                entity.getDurationSeconds(), entity.getContent(), entity.getRemark(), entity.getVersion(),
                entity.getCreateTime(), entity.getUpdateTime(), zone);
    }

    public StudyLogDto toDto(StudyLogRow row, ZoneId zone) {
        return base(row.getId(), row.getStudentId(), row.getSubjectId(), row.getSubjectName(), row.getStudyDate(),
                row.getDurationSeconds(), row.getContent(), row.getRemark(), row.getVersion(), row.getCreateTime(),
                row.getUpdateTime(), zone);
    }

    private StudyLogDto base(Long id, Long studentId, Long subjectId, String subjectName, java.time.LocalDate studyDate,
            Integer durationSeconds, String content, String remark, Integer version,
            java.time.LocalDateTime createTime, java.time.LocalDateTime updateTime, ZoneId zone) {
        StudyLogDto dto = new StudyLogDto();
        dto.setId(ids.toString(id));
        dto.setStudentId(ids.toString(studentId));
        dto.setSubjectId(ids.toString(subjectId));
        dto.setSubjectName(subjectName);
        dto.setStudyDate(studyDate);
        dto.setDurationSeconds(durationSeconds);
        dto.setContent(content);
        dto.setRemark(remark);
        dto.setVersion(version);
        dto.setCreatedAt(createTime.atZone(zone).toOffsetDateTime());
        dto.setUpdatedAt(updateTime.atZone(zone).toOffsetDateTime());
        return dto;
    }

    private void apply(String studentId, String subjectId, java.time.LocalDate studyDate, Integer durationSeconds,
            String content, String remark, StudyLogEntity entity) {
        entity.setStudentId(ids.toLong(studentId));
        entity.setSubjectId(subjectId == null ? null : ids.toLong(subjectId));
        entity.setStudyDate(studyDate);
        entity.setDurationSeconds(durationSeconds);
        entity.setContent(content);
        entity.setRemark(remark);
    }
}
