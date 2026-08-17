package com.stdntedu.resource.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ResourceHistoryRow {
    private Long id;
    private Long studentId;
    private Long resourceId;
    private String resourceTitle;
    private String resourceType;
    private String sourceType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private BigDecimal progressPercent;
    private Boolean completed;
    private String note;
    private LocalDateTime createTime;
}
