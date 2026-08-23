package com.stdntedu.growth.report.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("growth_report")
public class GrowthReportEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private String reportType;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String generationType;
    private String status;
    private String requestJson;
    private Long sourceReportId;
    private Integer snapshotSchemaVersion;
    private String generationVersion;
    private Integer progressPercent;
    private Boolean cancelRequested;
    private String statisticsSnapshotJson;
    private Long aiAnalysisId;
    private String contentMarkdown;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
