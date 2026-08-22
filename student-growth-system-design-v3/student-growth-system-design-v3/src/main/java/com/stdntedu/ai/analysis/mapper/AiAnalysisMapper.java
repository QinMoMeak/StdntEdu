package com.stdntedu.ai.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAnalysisMapper extends BaseMapper<AiAnalysisEntity> {
    @Insert("""
            INSERT INTO ai_analysis(student_id,business_type,business_id,ai_model_id,prompt_template_id,status,
                input_summary,input_json,idempotency_key,request_hash,result_json,error_code,error_message,
                prompt_tokens,completion_tokens,duration_ms,started_time,finished_time,estimated_cost,currency_code)
            VALUES (#{item.studentId},#{item.businessType},NULL,#{item.aiModelId},NULL,'PENDING',
                #{item.inputSummary},CAST(#{item.inputJson} AS JSON),#{item.idempotencyKey},#{item.requestHash},
                NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)
            ON DUPLICATE KEY UPDATE id=id
            """)
    int insertPending(@Param("item") AiAnalysisEntity item);

    @Select("""
            SELECT * FROM ai_analysis
             WHERE student_id=#{studentId} AND business_type='STUDY_PLAN_GENERATION'
               AND idempotency_key=#{idempotencyKey}
            """)
    AiAnalysisEntity selectExistingByIdempotency(@Param("studentId") Long studentId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM ai_analysis
             WHERE student_id=#{studentId} AND business_type='STUDY_PLAN_GENERATION'
               AND idempotency_key=#{idempotencyKey}
             FOR UPDATE
            """)
    AiAnalysisEntity selectByIdempotency(@Param("studentId") Long studentId,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE ai_analysis SET status='RUNNING',started_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND business_type='STUDY_PLAN_GENERATION' AND status='PENDING'
            """)
    int claim(@Param("id") Long id);

    @Update("""
            UPDATE ai_analysis
               SET status='SUCCESS',result_json=CAST(#{resultJson} AS JSON),error_code=NULL,error_message=NULL,
                   prompt_tokens=#{promptTokens},completion_tokens=#{completionTokens},estimated_cost=NULL,
                   currency_code=NULL,finished_time=CURRENT_TIMESTAMP(3),
                   duration_ms=GREATEST(0,TIMESTAMPDIFF(MICROSECOND,started_time,CURRENT_TIMESTAMP(3)) DIV 1000)
             WHERE id=#{id} AND status='RUNNING'
            """)
    int markSuccess(@Param("id") Long id, @Param("resultJson") String resultJson,
            @Param("promptTokens") Integer promptTokens, @Param("completionTokens") Integer completionTokens);

    @Update("""
            UPDATE ai_analysis
               SET status='FAILED',result_json=NULL,error_code=#{errorCode},error_message=#{errorMessage},
                   prompt_tokens=#{promptTokens},completion_tokens=#{completionTokens},estimated_cost=NULL,
                   currency_code=NULL,finished_time=CURRENT_TIMESTAMP(3),
                   duration_ms=GREATEST(0,TIMESTAMPDIFF(MICROSECOND,started_time,CURRENT_TIMESTAMP(3)) DIV 1000)
             WHERE id=#{id} AND status='RUNNING'
            """)
    int markFailed(@Param("id") Long id, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("promptTokens") Integer promptTokens,
            @Param("completionTokens") Integer completionTokens);

    @Select("""
            SELECT id FROM ai_analysis
             WHERE business_type='STUDY_PLAN_GENERATION' AND status=#{status} AND id>#{afterId}
             ORDER BY id ASC LIMIT #{limit}
            """)
    List<Long> selectIdsByStatusAfter(@Param("status") String status,
            @Param("afterId") Long afterId, @Param("limit") int limit);

    @Update("""
            UPDATE ai_analysis
               SET status='PENDING',started_time=NULL,finished_time=NULL,duration_ms=NULL,
                   result_json=NULL,error_code=NULL,error_message=NULL,prompt_tokens=NULL,completion_tokens=NULL
             WHERE id=#{id} AND business_type='STUDY_PLAN_GENERATION' AND status='RUNNING'
            """)
    int resetRunning(@Param("id") Long id);

    @Update("""
            UPDATE ai_analysis
               SET status='FAILED',started_time=COALESCE(started_time,CURRENT_TIMESTAMP(3)),
                   finished_time=CURRENT_TIMESTAMP(3),duration_ms=0,result_json=NULL,
                   error_code='ANALYSIS_PLAN_CONFLICT',error_message='analysis already has a persisted study plan'
             WHERE id=#{id} AND business_type='STUDY_PLAN_GENERATION' AND status IN ('PENDING','RUNNING')
            """)
    int markRecoveryConflict(@Param("id") Long id);
}
