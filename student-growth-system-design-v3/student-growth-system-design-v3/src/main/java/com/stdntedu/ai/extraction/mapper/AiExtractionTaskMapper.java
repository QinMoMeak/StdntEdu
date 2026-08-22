package com.stdntedu.ai.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiExtractionTaskMapper extends BaseMapper<AiExtractionTaskEntity> {
    @Update("""
            UPDATE ai_extraction_task
               SET status='RUNNING', progress_stage='PROVIDER_REQUEST', progress_percent=30,
                   started_time=COALESCE(started_time, CURRENT_TIMESTAMP(3)), error_code=NULL, error_message=NULL
             WHERE id=#{id} AND status=#{expected}
            """)
    int start(@Param("id") Long id, @Param("expected") String expected);

    @Update("""
            UPDATE ai_extraction_task
               SET status='REVIEW_REQUIRED', progress_stage='REVIEW', progress_percent=100,
                   finished_time=CURRENT_TIMESTAMP(3), error_code=NULL, error_message=NULL
             WHERE id=#{id} AND status='RUNNING'
            """)
    int markReviewRequired(@Param("id") Long id);

    @Update("""
            UPDATE ai_extraction_task
               SET status='FAILED', progress_stage='FAILED', error_code=#{code}, error_message=#{message},
                   finished_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND status='RUNNING'
            """)
    int markFailed(@Param("id") Long id, @Param("code") String code, @Param("message") String message);

    @Update("""
            UPDATE ai_extraction_task
               SET status='CANCELLED', progress_stage='CANCELLED', error_code='CANCELLED',
                   error_message=#{reason}, finished_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND status IN ('PENDING','RUNNING','REVIEW_REQUIRED')
            """)
    int cancel(@Param("id") Long id, @Param("reason") String reason);

    @Update("""
            UPDATE ai_extraction_task
               SET status='PENDING', progress_stage='QUEUED', progress_percent=0,
                   model_id=#{modelId}, retry_count=retry_count+1, error_code=NULL, error_message=NULL,
                   started_time=NULL, finished_time=NULL
             WHERE id=#{id} AND status=#{expected} AND retry_count < max_retry_count
            """)
    int retry(@Param("id") Long id, @Param("expected") String expected, @Param("modelId") Long modelId);

    @Update("""
            UPDATE ai_extraction_task
               SET status='SUCCESS', progress_stage='CONFIRMED', progress_percent=100,
                   finished_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND status='REVIEW_REQUIRED'
            """)
    int markConfirmed(@Param("id") Long id);

    @Update("""
            UPDATE ai_extraction_task
               SET status='PENDING', progress_stage='RECOVERED', progress_percent=0,
                   started_time=NULL, finished_time=NULL, error_code=NULL, error_message=NULL
             WHERE id=#{id} AND status='RUNNING'
            """)
    int resetRunning(@Param("id") Long id);

    @Update("""
            UPDATE ai_extraction_task
               SET status='REVIEW_REQUIRED', progress_stage='REVIEW', progress_percent=100,
                   finished_time=CURRENT_TIMESTAMP(3), error_code=NULL, error_message=NULL
             WHERE id=#{id} AND status='RUNNING'
            """)
    int recoverReviewRequired(@Param("id") Long id);

    @Select("""
            SELECT id FROM ai_extraction_task
             WHERE status=#{status} AND id>#{afterId}
             ORDER BY id ASC LIMIT #{limit}
            """)
    java.util.List<Long> selectIdsByStatusAfter(@Param("status") String status,
            @Param("afterId") Long afterId, @Param("limit") int limit);
}
