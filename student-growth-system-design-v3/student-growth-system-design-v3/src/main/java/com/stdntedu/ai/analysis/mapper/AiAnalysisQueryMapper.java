package com.stdntedu.ai.analysis.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.stdntedu.ai.analysis.projection.AiAnalysisRow;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiTaskStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAnalysisQueryMapper {
    @Select("""
            <script>
            SELECT COUNT(*) FROM ai_analysis aa
             WHERE 1=1
            <if test='studentId != null'> AND aa.student_id=#{studentId}</if>
            <if test='businessType != null'> AND aa.business_type=#{businessType}</if>
            <if test='businessId != null'> AND aa.business_id=#{businessId}</if>
            <if test='modelId != null'> AND aa.ai_model_id=#{modelId}</if>
            <if test='status != null'> AND aa.status=#{status}</if>
            <if test='startTime != null'> AND aa.create_time &gt;= #{startTime}</if>
            <if test='endTime != null'> AND aa.create_time &lt; #{endTime}</if>
            </script>
            """)
    long count(@Param("studentId") Long studentId,
            @Param("businessType") AiAnalysisBusinessType businessType,
            @Param("businessId") Long businessId, @Param("modelId") Long modelId,
            @Param("status") AiTaskStatus status, @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT aa.id, aa.student_id AS studentId, aa.business_type AS businessType,
                   aa.business_id AS businessId, aa.ai_model_id AS aiModelId,
                   am.model_name AS modelName, aa.prompt_template_id AS promptTemplateId,
                   aa.status, aa.input_summary AS inputSummary, aa.result_json AS resultJson,
                   aa.error_code AS errorCode, aa.error_message AS errorMessage,
                   aa.prompt_tokens AS promptTokens, aa.completion_tokens AS completionTokens,
                   aa.duration_ms AS durationMs, aa.started_time AS startedTime,
                   aa.finished_time AS finishedTime, aa.estimated_cost AS estimatedCost,
                   aa.currency_code AS currencyCode, aa.create_time AS createTime
              FROM ai_analysis aa
              JOIN ai_model am ON am.id=aa.ai_model_id
             WHERE 1=1
            <if test='studentId != null'> AND aa.student_id=#{studentId}</if>
            <if test='businessType != null'> AND aa.business_type=#{businessType}</if>
            <if test='businessId != null'> AND aa.business_id=#{businessId}</if>
            <if test='modelId != null'> AND aa.ai_model_id=#{modelId}</if>
            <if test='status != null'> AND aa.status=#{status}</if>
            <if test='startTime != null'> AND aa.create_time &gt;= #{startTime}</if>
            <if test='endTime != null'> AND aa.create_time &lt; #{endTime}</if>
             ORDER BY aa.create_time DESC, aa.id DESC
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<AiAnalysisRow> selectPage(@Param("studentId") Long studentId,
            @Param("businessType") AiAnalysisBusinessType businessType,
            @Param("businessId") Long businessId, @Param("modelId") Long modelId,
            @Param("status") AiTaskStatus status, @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            SELECT aa.id, aa.student_id AS studentId, aa.business_type AS businessType,
                   aa.business_id AS businessId, aa.ai_model_id AS aiModelId,
                   am.model_name AS modelName, aa.prompt_template_id AS promptTemplateId,
                   aa.status, aa.input_summary AS inputSummary, aa.result_json AS resultJson,
                   aa.error_code AS errorCode, aa.error_message AS errorMessage,
                   aa.prompt_tokens AS promptTokens, aa.completion_tokens AS completionTokens,
                   aa.duration_ms AS durationMs, aa.started_time AS startedTime,
                   aa.finished_time AS finishedTime, aa.estimated_cost AS estimatedCost,
                   aa.currency_code AS currencyCode, aa.create_time AS createTime
              FROM ai_analysis aa
              JOIN ai_model am ON am.id=aa.ai_model_id
             WHERE aa.id=#{id}
            """)
    AiAnalysisRow selectOne(@Param("id") Long id);
}
