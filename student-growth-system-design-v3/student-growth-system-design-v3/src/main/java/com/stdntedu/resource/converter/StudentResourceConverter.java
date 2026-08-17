package com.stdntedu.resource.converter;

import java.time.ZoneId;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.StudentResourceDto;
import com.stdntedu.resource.mapper.StudentResourceRow;
import org.springframework.stereotype.Component;

@Component
public class StudentResourceConverter {
    private final IdConverter ids;

    public StudentResourceConverter(IdConverter ids) {
        this.ids = ids;
    }

    public StudentResourceDto toDto(StudentResourceRow row, ZoneId zone) {
        return new StudentResourceDto()
                .id(ids.toString(row.getId()))
                .studentId(ids.toString(row.getStudentId()))
                .resourceId(ids.toString(row.getResourceId()))
                .resourceTitle(row.getResourceTitle())
                .resourceType(row.getResourceType())
                .sourceType(row.getSourceType())
                .subjectId(ids.toString(row.getSubjectId()))
                .subjectName(row.getSubjectName())
                .resourceStatus(ResourceStatus.fromValue(row.getResourceStatus()))
                .studentStatus(row.getStudentStatus())
                .latestProgressPercent(row.getLatestProgressPercent())
                .assignedTime(row.getAssignedTime().atZone(zone).toOffsetDateTime())
                .remark(row.getRemark())
                .version(row.getVersion())
                .createdAt(row.getCreateTime().atZone(zone).toOffsetDateTime())
                .updatedAt(row.getUpdateTime().atZone(zone).toOffsetDateTime());
    }
}
