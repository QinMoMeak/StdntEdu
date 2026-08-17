package com.stdntedu.resource.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stdntedu.generated.model.StudentResourceStatus;
import lombok.Data;

@Data
public class StudentResourceRow {
    private Long id;
    private Long studentId;
    private Long resourceId;
    private String resourceTitle;
    private String resourceType;
    private String sourceType;
    private Long subjectId;
    private String subjectName;
    private String resourceStatus;
    private StudentResourceStatus studentStatus;
    private BigDecimal latestProgressPercent;
    private LocalDateTime assignedTime;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
