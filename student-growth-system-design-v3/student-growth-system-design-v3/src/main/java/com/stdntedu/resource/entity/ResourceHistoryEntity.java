package com.stdntedu.resource.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resource_history")
public class ResourceHistoryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long resourceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private BigDecimal progressPercent;
    private Boolean completed;
    private String note;
    private LocalDateTime createTime;
}
