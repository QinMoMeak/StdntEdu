package com.stdntedu.student.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("student")
public class StudentEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String studentCode;
    private String name;
    private LocalDate birthday;
    private String school;
    private Long currentStageId;
    private Long currentGradeId;
    private String remark;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
