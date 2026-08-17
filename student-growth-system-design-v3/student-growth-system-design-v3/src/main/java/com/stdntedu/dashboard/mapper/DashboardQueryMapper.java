package com.stdntedu.dashboard.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.stdntedu.score.entity.ExamEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardQueryMapper {
    @Select("""
            SELECT COALESCE(SUM(duration_seconds), 0)
              FROM study_log
             WHERE student_id = #{studentId} AND deleted = 0 AND study_date = #{targetDate}
            """)
    int sumStudyDuration(@Param("studentId") Long studentId, @Param("targetDate") LocalDate targetDate);

    @Select("""
            SELECT CAST(COUNT(*) AS SIGNED) AS totalTaskCount,
                   CAST(COALESCE(SUM(task.status = 'COMPLETED'), 0) AS SIGNED) AS completedTaskCount
              FROM study_plan plan
              JOIN study_plan_task task ON task.study_plan_id = plan.id
             WHERE plan.student_id = #{studentId}
               AND plan.deleted = 0
               AND plan.status = 'ACTIVE'
               AND task.task_date = #{targetDate}
               AND task.status <> 'CANCELLED'
            """)
    DashboardTaskCountRow selectTaskCounts(@Param("studentId") Long studentId,
            @Param("targetDate") LocalDate targetDate);

    @Select("""
            SELECT CAST(COALESCE(SUM(next_review_time < #{nextDayStart}), 0) AS SIGNED) AS dueReviewCount,
                   CAST(COALESCE(SUM(next_review_time < #{dayStart}), 0) AS SIGNED) AS overdueReviewCount
              FROM wrong_question
             WHERE student_id = #{studentId}
               AND deleted = 0
               AND status <> 'ARCHIVED'
               AND next_review_time IS NOT NULL
            """)
    DashboardReviewCountRow selectReviewCounts(@Param("studentId") Long studentId,
            @Param("dayStart") LocalDateTime dayStart, @Param("nextDayStart") LocalDateTime nextDayStart);

    @Select("""
            SELECT CAST(COALESCE(SUM(assignment.status = 'WAITING'), 0) AS SIGNED) AS waitingResourceCount,
                   CAST(COALESCE(SUM(assignment.status = 'LEARNING'), 0) AS SIGNED) AS learningResourceCount
              FROM student_resource_assignment assignment
              JOIN learning_resource resource ON resource.id = assignment.resource_id
             WHERE assignment.student_id = #{studentId}
               AND resource.deleted = 0
               AND resource.status <> 'ARCHIVED'
            """)
    DashboardResourceCountRow selectResourceCounts(@Param("studentId") Long studentId);

    @Select("""
            <script>
            SELECT id, student_id AS studentId, academic_term_id AS academicTermId, exam_name AS examName,
                   exam_type AS examType, exam_date AS examDate, total_score AS totalScore,
                   total_full_score AS totalFullScore, remark, deleted, version,
                   create_time AS createTime, update_time AS updateTime
              FROM exam
             WHERE student_id = #{studentId} AND deleted = 0
            <if test='academicTermId != null'> AND academic_term_id = #{academicTermId}</if>
             ORDER BY exam_date DESC, create_time DESC, id DESC
             LIMIT 1
            </script>
            """)
    ExamEntity selectLatestExam(@Param("studentId") Long studentId,
            @Param("academicTermId") Long academicTermId);

    @Select("""
            SELECT recent.id, recent.studentId, recent.academicTermId, recent.examName, recent.examType,
                   recent.examDate, recent.totalScore, recent.totalFullScore, recent.remark, recent.deleted,
                   recent.version, recent.createTime, recent.updateTime
              FROM (
                    SELECT id, student_id AS studentId, academic_term_id AS academicTermId,
                           exam_name AS examName, exam_type AS examType, exam_date AS examDate,
                           total_score AS totalScore, total_full_score AS totalFullScore, remark, deleted, version,
                           create_time AS createTime, update_time AS updateTime
                      FROM exam
                     WHERE student_id = #{studentId}
                       AND deleted = 0
                       AND exam_date BETWEEN #{periodStart} AND #{periodEnd}
                       AND total_score IS NOT NULL
                       AND total_full_score IS NOT NULL
                       AND total_full_score > 0
                     ORDER BY exam_date DESC, create_time DESC, id DESC
                     LIMIT 10
                   ) recent
             ORDER BY recent.examDate ASC, recent.createTime ASC, recent.id ASC
            """)
    List<ExamEntity> selectScoreTrends(@Param("studentId") Long studentId,
            @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    @Select("""
            SELECT mastery.knowledge_id AS knowledgeId, knowledge.node_code AS knowledgeCode,
                   knowledge.name AS knowledgeName, knowledge.subject_id AS subjectId,
                   subject.name AS subjectName, mastery.mastery_score AS masteryScore,
                   mastery.evidence_count AS evidenceCount, mastery.next_review_time AS nextReviewTime
              FROM student_mastery mastery
              JOIN knowledge_node knowledge ON knowledge.id = mastery.knowledge_id
              LEFT JOIN subject ON subject.id = knowledge.subject_id
             WHERE mastery.student_id = #{studentId}
             ORDER BY mastery.mastery_score ASC, mastery.evidence_count DESC,
                      mastery.update_time DESC, mastery.id ASC
             LIMIT 5
            """)
    List<DashboardMasteryRow> selectWeakMastery(@Param("studentId") Long studentId);

    @Select("""
            SELECT question.id, question.subject_id AS subjectId, subject.name AS subjectName,
                   question.source_type AS sourceType, question.question_type AS questionType,
                   question.question_text AS questionText, question.status,
                   question.review_stage AS reviewStage, question.next_review_time AS nextReviewTime
              FROM wrong_question question
              JOIN subject ON subject.id = question.subject_id
             WHERE question.student_id = #{studentId}
               AND question.deleted = 0
               AND question.status <> 'ARCHIVED'
               AND question.next_review_time IS NOT NULL
               AND question.next_review_time < #{nextDayStart}
             ORDER BY question.next_review_time ASC, question.id ASC
             LIMIT 5
            """)
    List<DashboardWrongQuestionRow> selectDueReviews(@Param("studentId") Long studentId,
            @Param("nextDayStart") LocalDateTime nextDayStart);

    @Select("""
            SELECT assignment.resource_id AS resourceId, resource.title,
                   resource.resource_type AS resourceType, resource.source_type AS sourceType,
                   resource.status AS resourceStatus, assignment.status AS studentStatus,
                   assignment.id AS assignmentId, assignment.assigned_time AS assignedTime,
                   resource.duration_seconds AS durationSeconds,
                   latest.progress_percent AS latestProgressPercent
              FROM student_resource_assignment assignment
              JOIN learning_resource resource ON resource.id = assignment.resource_id
              LEFT JOIN (
                    SELECT ranked.student_id, ranked.resource_id, ranked.progress_percent
                      FROM (
                            SELECT history.student_id, history.resource_id, history.progress_percent,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY history.student_id, history.resource_id
                                       ORDER BY history.create_time DESC, history.id DESC
                                   ) AS history_rank
                              FROM resource_history history
                             WHERE history.student_id = #{studentId}
                           ) ranked
                     WHERE ranked.history_rank = 1
                   ) latest ON latest.student_id = assignment.student_id
                           AND latest.resource_id = assignment.resource_id
             WHERE assignment.student_id = #{studentId}
               AND assignment.status = 'WAITING'
               AND resource.deleted = 0
               AND resource.status <> 'ARCHIVED'
             ORDER BY assignment.assigned_time DESC, assignment.id DESC
             LIMIT 5
            """)
    List<DashboardResourceRow> selectWaitingResources(@Param("studentId") Long studentId);
}
