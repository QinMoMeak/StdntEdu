package com.stdntedu.knowledge.mastery.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MasteryEvidenceMapper {
    @Select("""
            SELECT sk.id AS business_id, CAST(e.exam_date AS DATETIME) AS event_time,
                   sk.score, sk.full_score, sk.correct_count, sk.question_count
              FROM score_knowledge sk
              JOIN score_record sr ON sr.id=sk.score_record_id AND sr.deleted=0
              JOIN exam e ON e.id=sr.exam_id AND e.deleted=0
             WHERE e.student_id=#{studentId} AND sk.knowledge_id=#{knowledgeId}
            """)
    List<MasteryEvidenceRow> selectExamEvidence(@Param("studentId") Long studentId,
            @Param("knowledgeId") Long knowledgeId);

    @Select("""
            SELECT wq.id AS business_id,
                   COALESCE(CAST(wq.occurred_date AS DATETIME), wq.create_time) AS event_time,
                   'WRONG' AS result
              FROM wrong_question wq
              JOIN wrong_question_knowledge wqk ON wqk.wrong_question_id=wq.id
             WHERE wq.student_id=#{studentId} AND wqk.knowledge_id=#{knowledgeId} AND wq.deleted=0
            """)
    List<MasteryEvidenceRow> selectPracticeEvidence(@Param("studentId") Long studentId,
            @Param("knowledgeId") Long knowledgeId);

    @Select("""
            SELECT wr.id AS business_id, wr.review_time AS event_time, wr.result
              FROM wrong_review wr
              JOIN wrong_question wq ON wq.id=wr.wrong_question_id AND wq.deleted=0
              JOIN wrong_question_knowledge wqk ON wqk.wrong_question_id=wq.id
             WHERE wq.student_id=#{studentId} AND wqk.knowledge_id=#{knowledgeId}
            """)
    List<MasteryEvidenceRow> selectReviewEvidence(@Param("studentId") Long studentId,
            @Param("knowledgeId") Long knowledgeId);

    @Select("""
            SELECT MIN(wq.next_review_time)
              FROM wrong_question wq
              JOIN wrong_question_knowledge wqk ON wqk.wrong_question_id=wq.id
             WHERE wq.student_id=#{studentId} AND wqk.knowledge_id=#{knowledgeId}
               AND wq.deleted=0 AND wq.status!='ARCHIVED' AND wq.next_review_time IS NOT NULL
            """)
    LocalDateTime selectNextReviewTime(@Param("studentId") Long studentId,
            @Param("knowledgeId") Long knowledgeId);
}
