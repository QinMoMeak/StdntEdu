package com.stdntedu.resource.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceHistoryQueryMapper {
    @Select("""
            <script>
            SELECT rh.id, rh.student_id AS studentId, rh.resource_id AS resourceId,
                   lr.title AS resourceTitle, lr.resource_type AS resourceType, lr.source_type AS sourceType,
                   rh.start_time AS startTime, rh.end_time AS endTime, rh.duration_seconds AS durationSeconds,
                   rh.progress_percent AS progressPercent, rh.completed, rh.note, rh.create_time AS createTime
            FROM resource_history rh
            JOIN learning_resource lr ON lr.id = rh.resource_id AND lr.deleted = 0
            WHERE rh.student_id = #{studentId}
            <if test='resourceId != null'> AND rh.resource_id = #{resourceId}</if>
            <if test='subjectId != null'> AND lr.subject_id = #{subjectId}</if>
            <if test='resourceType != null and resourceType != ""'> AND lr.resource_type = #{resourceType}</if>
            <if test='sourceType != null and sourceType != ""'> AND lr.source_type = #{sourceType}</if>
            <if test='completed != null'> AND rh.completed = #{completed}</if>
            <if test='startDate != null'> AND rh.create_time &gt;= #{startDate}</if>
            <if test='endDate != null'> AND rh.create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>
            ORDER BY rh.create_time DESC, rh.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<ResourceHistoryRow> selectPage(@Param("studentId") Long studentId, @Param("resourceId") Long resourceId,
            @Param("subjectId") Long subjectId, @Param("resourceType") String resourceType,
            @Param("sourceType") String sourceType, @Param("completed") Boolean completed,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM resource_history rh
            JOIN learning_resource lr ON lr.id = rh.resource_id AND lr.deleted = 0
            WHERE rh.student_id = #{studentId}
            <if test='resourceId != null'> AND rh.resource_id = #{resourceId}</if>
            <if test='subjectId != null'> AND lr.subject_id = #{subjectId}</if>
            <if test='resourceType != null and resourceType != ""'> AND lr.resource_type = #{resourceType}</if>
            <if test='sourceType != null and sourceType != ""'> AND lr.source_type = #{sourceType}</if>
            <if test='completed != null'> AND rh.completed = #{completed}</if>
            <if test='startDate != null'> AND rh.create_time &gt;= #{startDate}</if>
            <if test='endDate != null'> AND rh.create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>
            </script>
            """)
    long count(@Param("studentId") Long studentId, @Param("resourceId") Long resourceId,
            @Param("subjectId") Long subjectId, @Param("resourceType") String resourceType,
            @Param("sourceType") String sourceType, @Param("completed") Boolean completed,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
