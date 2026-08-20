package com.stdntedu.studyplan.mapper;

import java.time.LocalDate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.generated.model.StudyPlanStatus;
import com.stdntedu.studyplan.entity.StudyPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudyPlanMapper extends BaseMapper<StudyPlanEntity> {
    @Select("SELECT COUNT(*) FROM study_plan WHERE source_analysis_id=#{analysisId}")
    long countBySourceAnalysisId(@Param("analysisId") Long analysisId);
    @Update("""
            UPDATE study_plan
               SET title=#{title}, plan_type=#{planType}, start_date=#{startDate}, end_date=#{endDate},
                   daily_available_minutes=#{dailyAvailableMinutes}, description=#{description},
                   version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND deleted=0
            """)
    int updateMetadataWithVersion(@Param("id") Long id, @Param("title") String title,
            @Param("planType") String planType, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("dailyAvailableMinutes") Integer dailyAvailableMinutes,
            @Param("description") String description, @Param("version") Integer version);

    @Update("""
            UPDATE study_plan
               SET status=#{targetStatus}, version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND status=#{currentStatus} AND deleted=0
            """)
    int transitionWithVersion(@Param("id") Long id, @Param("version") Integer version,
            @Param("currentStatus") StudyPlanStatus currentStatus,
            @Param("targetStatus") StudyPlanStatus targetStatus);
}
