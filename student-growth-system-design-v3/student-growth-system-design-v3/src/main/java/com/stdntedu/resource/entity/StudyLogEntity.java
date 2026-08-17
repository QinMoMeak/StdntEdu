package com.stdntedu.resource.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("study_log")
public class StudyLogEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long subjectId;
    private LocalDate studyDate;
    private Integer durationSeconds;
    private String content;
    private String remark;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
