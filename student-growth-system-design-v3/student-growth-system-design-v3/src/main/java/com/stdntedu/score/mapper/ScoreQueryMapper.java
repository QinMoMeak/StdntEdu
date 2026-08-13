package com.stdntedu.score.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScoreQueryMapper {
    @Select("""
            <script>
            SELECT sr.id, sr.exam_id AS examId, e.exam_name AS examName, e.exam_type AS examType,
                   e.exam_date AS examDate, e.academic_term_id AS academicTermId, sr.subject_id AS subjectId,
                   s.name AS subjectName, sr.score, sr.full_score AS fullScore, sr.class_rank AS classRank,
                   sr.grade_rank AS gradeRank
            FROM score_record sr
            JOIN exam e ON e.id = sr.exam_id AND e.deleted = 0
            JOIN subject s ON s.id = sr.subject_id
            WHERE sr.deleted = 0 AND sr.student_id = #{studentId}
            <if test='academicTermId != null'> AND e.academic_term_id = #{academicTermId}</if>
            <if test='subjectId != null'> AND sr.subject_id = #{subjectId}</if>
            <if test='examType != null'> AND e.exam_type = #{examType}</if>
            <if test='startDate != null'> AND e.exam_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND e.exam_date &lt;= #{endDate}</if>
            <if test='keyword != null and keyword != ""'> AND e.exam_name LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY e.exam_date DESC, e.create_time DESC, sr.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<ScoreListRow> selectPage(@Param("studentId") Long studentId, @Param("academicTermId") Long academicTermId,
            @Param("subjectId") Long subjectId, @Param("examType") String examType,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") long limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM score_record sr
            JOIN exam e ON e.id = sr.exam_id AND e.deleted = 0
            WHERE sr.deleted = 0 AND sr.student_id = #{studentId}
            <if test='academicTermId != null'> AND e.academic_term_id = #{academicTermId}</if>
            <if test='subjectId != null'> AND sr.subject_id = #{subjectId}</if>
            <if test='examType != null'> AND e.exam_type = #{examType}</if>
            <if test='startDate != null'> AND e.exam_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND e.exam_date &lt;= #{endDate}</if>
            <if test='keyword != null and keyword != ""'> AND e.exam_name LIKE CONCAT('%', #{keyword}, '%')</if>
            </script>
            """)
    long count(@Param("studentId") Long studentId, @Param("academicTermId") Long academicTermId,
            @Param("subjectId") Long subjectId, @Param("examType") String examType,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword);
}
