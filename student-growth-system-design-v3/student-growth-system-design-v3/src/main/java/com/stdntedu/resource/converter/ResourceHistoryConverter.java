package com.stdntedu.resource.converter;

import java.time.ZoneId;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ResourceHistoryCreateRequest;
import com.stdntedu.generated.model.ResourceHistoryDto;
import com.stdntedu.resource.entity.LearningResourceEntity;
import com.stdntedu.resource.entity.ResourceHistoryEntity;
import com.stdntedu.resource.mapper.ResourceHistoryRow;
import org.springframework.stereotype.Component;

@Component
public class ResourceHistoryConverter {
    private final IdConverter ids;

    public ResourceHistoryConverter(IdConverter ids) {
        this.ids = ids;
    }

    public ResourceHistoryEntity fromCreate(Long resourceId, Long studentId, ResourceHistoryCreateRequest request,
            ZoneId zone) {
        ResourceHistoryEntity entity = new ResourceHistoryEntity();
        entity.setResourceId(resourceId);
        entity.setStudentId(studentId);
        entity.setStartTime(request.getStartTime() == null ? null
                : request.getStartTime().atZoneSameInstant(zone).toLocalDateTime());
        entity.setEndTime(request.getEndTime() == null ? null
                : request.getEndTime().atZoneSameInstant(zone).toLocalDateTime());
        entity.setDurationSeconds(request.getDurationSeconds());
        entity.setProgressPercent(request.getProgressPercent());
        entity.setCompleted(request.getCompleted());
        entity.setNote(request.getNote());
        return entity;
    }

    public ResourceHistoryDto toDto(ResourceHistoryEntity entity, LearningResourceEntity resource, ZoneId zone) {
        return base(entity.getId(), entity.getStudentId(), entity.getResourceId(), resource.getTitle(),
                resource.getResourceType(), resource.getSourceType(), entity.getStartTime(), entity.getEndTime(),
                entity.getDurationSeconds(), entity.getProgressPercent(), entity.getCompleted(), entity.getNote(),
                entity.getCreateTime(), zone);
    }

    public ResourceHistoryDto toDto(ResourceHistoryRow row, ZoneId zone) {
        return base(row.getId(), row.getStudentId(), row.getResourceId(), row.getResourceTitle(), row.getResourceType(),
                row.getSourceType(), row.getStartTime(), row.getEndTime(), row.getDurationSeconds(),
                row.getProgressPercent(), row.getCompleted(), row.getNote(), row.getCreateTime(), zone);
    }

    private ResourceHistoryDto base(Long id, Long studentId, Long resourceId, String title, String resourceType,
            String sourceType, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
            Integer durationSeconds, java.math.BigDecimal progressPercent, Boolean completed, String note,
            java.time.LocalDateTime createTime, ZoneId zone) {
        ResourceHistoryDto dto = new ResourceHistoryDto();
        dto.setId(ids.toString(id));
        dto.setStudentId(ids.toString(studentId));
        dto.setResourceId(ids.toString(resourceId));
        dto.setResourceTitle(title);
        dto.setResourceType(resourceType);
        dto.setSourceType(sourceType);
        dto.setStartTime(startTime == null ? null : startTime.atZone(zone).toOffsetDateTime());
        dto.setEndTime(endTime == null ? null : endTime.atZone(zone).toOffsetDateTime());
        dto.setDurationSeconds(durationSeconds);
        dto.setProgressPercent(progressPercent);
        dto.setCompleted(completed);
        dto.setNote(note);
        dto.setCreatedAt(createTime.atZone(zone).toOffsetDateTime());
        return dto;
    }
}
