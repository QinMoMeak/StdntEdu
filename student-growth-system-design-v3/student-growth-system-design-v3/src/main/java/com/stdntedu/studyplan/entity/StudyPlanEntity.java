package com.stdntedu.studyplan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.stdntedu.generated.model.StudyPlanStatus;
import lombok.Data;

@Data
@TableName("study_plan")
public class StudyPlanEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private String title;
    private String planType;
    private LocalDate startDate;
    private LocalDate endDate;
    private StudyPlanStatus status;
    private Long sourceAnalysisId;
    private Integer dailyAvailableMinutes;
    private String description;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
