package com.stdntedu.ai.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiExtractionQuestionMapper extends BaseMapper<AiExtractionQuestionEntity> {
    @Update("""
            UPDATE ai_extraction_question
               SET question_type=#{question.questionType}, question_text=#{question.questionText},
                   student_answer=#{question.studentAnswer}, correct_answer=#{question.correctAnswer},
                   analysis_text=#{question.analysisText}, error_type=#{question.errorType},
                   difficulty=#{question.difficulty}, status=#{question.status}, user_modified=1,
                   version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{question.id} AND task_id=#{question.taskId} AND version=#{version} AND status<>'SAVED'
            """)
    int updateWithVersion(@Param("question") AiExtractionQuestionEntity question,
            @Param("version") Integer version);

    @Update("""
            UPDATE ai_extraction_question
               SET status=#{status}, version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND task_id=#{taskId} AND status<>'SAVED'
            """)
    int updateStatus(@Param("id") Long id, @Param("taskId") Long taskId, @Param("status") String status);
}
