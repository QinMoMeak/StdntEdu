package com.stdntedu.growth.event.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("growth_event")
public class GrowthEventEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private String eventType;
    private String title;
    private LocalDate eventDate;
    private String description;
    private String tags;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false) private String eventTypeLabel;
}
