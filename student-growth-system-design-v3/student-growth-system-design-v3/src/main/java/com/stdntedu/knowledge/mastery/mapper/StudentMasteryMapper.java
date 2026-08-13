package com.stdntedu.knowledge.mastery.mapper;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.knowledge.mastery.entity.StudentMasteryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentMasteryMapper extends BaseMapper<StudentMasteryEntity> {
    @Select({"<script>",
            "SELECT sm.* FROM student_mastery sm JOIN knowledge_node kn ON kn.id=sm.knowledge_id AND kn.deleted=0",
            "WHERE sm.student_id=#{studentId}",
            "<if test='subjectId != null'>AND kn.subject_id=#{subjectId}</if>",
            "<if test='gradeId != null'>AND kn.grade_id=#{gradeId}</if>",
            "<if test='minScore != null'>AND sm.mastery_score &gt;= #{minScore}</if>",
            "<if test='maxScore != null'>AND sm.mastery_score &lt;= #{maxScore}</if>",
            "ORDER BY sm.mastery_score ASC, sm.knowledge_id ASC", "</script>"})
    List<StudentMasteryEntity> selectForList(@Param("studentId") Long studentId,
            @Param("subjectId") Long subjectId, @Param("gradeId") Long gradeId,
            @Param("minScore") BigDecimal minScore, @Param("maxScore") BigDecimal maxScore);

    @Delete("DELETE FROM student_mastery WHERE id=#{id} AND version=#{version}")
    int deleteWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
