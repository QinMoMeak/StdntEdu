package com.stdntedu.resource.mapper;

import java.util.List;

import com.stdntedu.generated.model.StudentResourceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentResourceQueryMapper {
    String SELECT_COLUMNS = """
            SELECT assignment.id,
                   assignment.student_id AS studentId,
                   assignment.resource_id AS resourceId,
                   resource.title AS resourceTitle,
                   resource.resource_type AS resourceType,
                   resource.source_type AS sourceType,
                   resource.subject_id AS subjectId,
                   subject.name AS subjectName,
                   resource.status AS resourceStatus,
                   assignment.status AS studentStatus,
                   (SELECT history.progress_percent
                      FROM resource_history history
                     WHERE history.student_id = assignment.student_id
                       AND history.resource_id = assignment.resource_id
                     ORDER BY history.create_time DESC, history.id DESC
                     LIMIT 1) AS latestProgressPercent,
                   assignment.assigned_time AS assignedTime,
                   assignment.remark,
                   assignment.version,
                   assignment.create_time AS createTime,
                   assignment.update_time AS updateTime
              FROM student_resource_assignment assignment
              JOIN learning_resource resource ON resource.id = assignment.resource_id
              LEFT JOIN subject ON subject.id = resource.subject_id
            """;

    @Select(SELECT_COLUMNS + " WHERE assignment.id = #{assignmentId}")
    StudentResourceRow selectDetail(@Param("assignmentId") Long assignmentId);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM student_resource_assignment assignment
              JOIN learning_resource resource ON resource.id = assignment.resource_id
             WHERE assignment.student_id = #{studentId}
            <if test='status != null'> AND assignment.status = #{status}</if>
            <if test='subjectId != null'> AND resource.subject_id = #{subjectId}</if>
            </script>
            """)
    long count(@Param("studentId") Long studentId, @Param("status") StudentResourceStatus status,
            @Param("subjectId") Long subjectId);

    @Select("""
            <script>
            """ + SELECT_COLUMNS + """
             WHERE assignment.student_id = #{studentId}
            <if test='status != null'> AND assignment.status = #{status}</if>
            <if test='subjectId != null'> AND resource.subject_id = #{subjectId}</if>
             ORDER BY assignment.assigned_time DESC, assignment.id DESC
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<StudentResourceRow> selectPage(@Param("studentId") Long studentId,
            @Param("status") StudentResourceStatus status, @Param("subjectId") Long subjectId,
            @Param("offset") long offset, @Param("limit") int limit);
}
