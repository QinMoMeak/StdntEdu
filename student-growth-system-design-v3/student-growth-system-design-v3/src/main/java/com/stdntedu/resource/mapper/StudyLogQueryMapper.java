package com.stdntedu.resource.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudyLogQueryMapper {
    @Select("""
            <script>
            SELECT sl.id, sl.student_id AS studentId, sl.subject_id AS subjectId, s.name AS subjectName,
                   sl.study_date AS studyDate, sl.duration_seconds AS durationSeconds, sl.content, sl.remark,
                   sl.version, sl.create_time AS createTime, sl.update_time AS updateTime
            FROM study_log sl
            LEFT JOIN subject s ON s.id = sl.subject_id
            WHERE sl.deleted = 0 AND sl.student_id = #{studentId}
            <if test='subjectId != null'> AND sl.subject_id = #{subjectId}</if>
            <if test='startDate != null'> AND sl.study_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND sl.study_date &lt;= #{endDate}</if>
            <if test='keyword != null and keyword != ""'>
              AND (sl.content LIKE CONCAT('%', #{keyword}, '%') OR sl.remark LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY sl.study_date DESC, sl.create_time DESC, sl.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<StudyLogRow> selectPage(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM study_log sl
            WHERE sl.deleted = 0 AND sl.student_id = #{studentId}
            <if test='subjectId != null'> AND sl.subject_id = #{subjectId}</if>
            <if test='startDate != null'> AND sl.study_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND sl.study_date &lt;= #{endDate}</if>
            <if test='keyword != null and keyword != ""'>
              AND (sl.content LIKE CONCAT('%', #{keyword}, '%') OR sl.remark LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long count(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword);
}
