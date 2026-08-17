package com.stdntedu.resource.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("learning_resource")
public class LearningResourceEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String resourceCode;
    private String title;
    private String resourceType;
    private String sourceType;
    private String sourceUrl;
    private Long subjectId;
    private Integer durationSeconds;
    private Integer difficulty;
    private String status;
    private String description;
    private String tags;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
