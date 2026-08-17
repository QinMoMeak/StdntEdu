package com.stdntedu.studyplan.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import com.stdntedu.studyplan.entity.StudyPlanTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StudyPlanTaskMapper extends BaseMapper<StudyPlanTaskEntity> {
    @Update("""
            UPDATE study_plan_task
               SET task_date=#{taskDate}, task_type=#{taskType}, title=#{title}, resource_id=#{resourceId},
                   wrong_question_id=#{wrongQuestionId}, knowledge_id=#{knowledgeId}, exam_id=#{examId},
                   expected_duration_seconds=#{expectedDurationSeconds}, sort_order=#{sortOrder},
                   remark=#{remark}, status=#{targetStatus}, version=version+1,
                   update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND study_plan_id=#{planId} AND version=#{version} AND status=#{currentStatus}
            """)
    int updateWithVersion(@Param("id") Long id, @Param("planId") Long planId,
            @Param("taskDate") LocalDate taskDate, @Param("taskType") StudyPlanTaskType taskType,
            @Param("title") String title, @Param("resourceId") Long resourceId,
            @Param("wrongQuestionId") Long wrongQuestionId, @Param("knowledgeId") Long knowledgeId,
            @Param("examId") Long examId, @Param("expectedDurationSeconds") Integer expectedDurationSeconds,
            @Param("sortOrder") Integer sortOrder, @Param("remark") String remark,
            @Param("currentStatus") StudyPlanTaskStatus currentStatus,
            @Param("targetStatus") StudyPlanTaskStatus targetStatus, @Param("version") Integer version);

    @Update("""
            UPDATE study_plan_task
               SET status=#{targetStatus}, actual_duration_seconds=#{actualDurationSeconds},
                   completed_time=#{completedTime}, version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND study_plan_id=#{planId} AND version=#{version} AND status=#{currentStatus}
            """)
    int transitionWithVersion(@Param("id") Long id, @Param("planId") Long planId,
            @Param("version") Integer version, @Param("currentStatus") StudyPlanTaskStatus currentStatus,
            @Param("targetStatus") StudyPlanTaskStatus targetStatus,
            @Param("actualDurationSeconds") Integer actualDurationSeconds,
            @Param("completedTime") LocalDateTime completedTime);
}
