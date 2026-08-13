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
@TableName("academic_term")
public class AcademicTermEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private String academicYear;
    private String semester;
    private Long stageId;
    private Long gradeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrent;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
