package com.stdntedu.dashboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DashboardResourceRow {
    private Long resourceId;
    private String title;
    private String resourceType;
    private String sourceType;
    private String resourceStatus;
    private String studentStatus;
    private Long assignmentId;
    private LocalDateTime assignedTime;
    private Integer durationSeconds;
    private BigDecimal latestProgressPercent;
}
