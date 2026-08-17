package com.stdntedu.studyplan.mapper;

import java.time.LocalDate;
import java.util.List;

import com.stdntedu.generated.model.StudyPlanStatus;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import com.stdntedu.studyplan.entity.StudyPlanEntity;
import com.stdntedu.studyplan.entity.StudyPlanTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudyPlanQueryMapper {
    @Select("""
            <script>
            SELECT COUNT(*) FROM study_plan
             WHERE student_id=#{studentId} AND deleted=0
            <if test='status != null'> AND status=#{status}</if>
            <if test='planType != null and planType != ""'> AND plan_type=#{planType}</if>
            <if test='startDate != null'> AND end_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND start_date &lt;= #{endDate}</if>
            </script>
            """)
    long countPlans(@Param("studentId") Long studentId, @Param("status") StudyPlanStatus status,
            @Param("planType") String planType, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Select("""
            <script>
            SELECT * FROM study_plan
             WHERE student_id=#{studentId} AND deleted=0
            <if test='status != null'> AND status=#{status}</if>
            <if test='planType != null and planType != ""'> AND plan_type=#{planType}</if>
            <if test='startDate != null'> AND end_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND start_date &lt;= #{endDate}</if>
             ORDER BY create_time DESC, id DESC
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<StudyPlanEntity> selectPlanPage(@Param("studentId") Long studentId,
            @Param("status") StudyPlanStatus status, @Param("planType") String planType,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM study_plan_task WHERE study_plan_id IN
            <foreach collection='planIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ORDER BY task_date ASC, sort_order ASC, id ASC
            </script>
            """)
    List<StudyPlanTaskEntity> selectTasksForPlans(@Param("planIds") List<Long> planIds);

    @Select("""
            <script>
            SELECT COUNT(*) FROM study_plan_task WHERE study_plan_id=#{planId}
            <if test='taskDate != null'> AND task_date=#{taskDate}</if>
            <if test='status != null'> AND status=#{status}</if>
            <if test='taskType != null'> AND task_type=#{taskType}</if>
            </script>
            """)
    long countTasks(@Param("planId") Long planId, @Param("taskDate") LocalDate taskDate,
            @Param("status") StudyPlanTaskStatus status, @Param("taskType") StudyPlanTaskType taskType);

    @Select("""
            <script>
            SELECT * FROM study_plan_task WHERE study_plan_id=#{planId}
            <if test='taskDate != null'> AND task_date=#{taskDate}</if>
            <if test='status != null'> AND status=#{status}</if>
            <if test='taskType != null'> AND task_type=#{taskType}</if>
             ORDER BY task_date ASC, sort_order ASC, id ASC
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<StudyPlanTaskEntity> selectTaskPage(@Param("planId") Long planId,
            @Param("taskDate") LocalDate taskDate, @Param("status") StudyPlanTaskStatus status,
            @Param("taskType") StudyPlanTaskType taskType, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM study_plan_task
             WHERE study_plan_id=#{planId} AND (task_date < #{startDate} OR task_date > #{endDate})
            """)
    long countTasksOutsideRange(@Param("planId") Long planId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
