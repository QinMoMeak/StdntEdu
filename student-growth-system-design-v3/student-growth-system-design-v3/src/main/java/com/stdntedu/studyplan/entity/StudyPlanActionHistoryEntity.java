package com.stdntedu.studyplan.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("study_plan_action_history")
public class StudyPlanActionHistoryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studyPlanId;
    private Long studyPlanTaskId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String note;
    private Integer versionBefore;
    private Integer versionAfter;
    private LocalDateTime createTime;
}
