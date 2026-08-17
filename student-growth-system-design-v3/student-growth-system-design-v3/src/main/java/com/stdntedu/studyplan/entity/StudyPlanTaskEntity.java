package com.stdntedu.studyplan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import lombok.Data;

@Data
@TableName("study_plan_task")
public class StudyPlanTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studyPlanId;
    private LocalDate taskDate;
    private StudyPlanTaskType taskType;
    private String title;
    private Long resourceId;
    private Long wrongQuestionId;
    private Long knowledgeId;
    private Long examId;
    private Integer expectedDurationSeconds;
    private Integer actualDurationSeconds;
    private StudyPlanTaskStatus status;
    private LocalDateTime completedTime;
    private Integer sortOrder;
    private String remark;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
