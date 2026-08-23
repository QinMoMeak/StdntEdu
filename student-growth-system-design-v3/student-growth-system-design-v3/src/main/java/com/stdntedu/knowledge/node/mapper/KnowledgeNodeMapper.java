package com.stdntedu.knowledge.node.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.knowledge.node.entity.KnowledgeNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeNodeMapper extends BaseMapper<KnowledgeNodeEntity> {
    @Select("""
            <script>
            SELECT id,parent_id,node_code,name,node_type,stage_id,grade_id,subject_id,level_no,sort_order,
                   difficulty,description,keywords,enabled,deleted,version,create_time,update_time
              FROM knowledge_node
             WHERE deleted=0
             <if test='stageId != null'>AND (stage_id=#{stageId} OR stage_id IS NULL)</if>
             <if test='gradeId != null'>AND (grade_id=#{gradeId} OR grade_id IS NULL)</if>
             <if test='subjectId != null'>AND (subject_id=#{subjectId} OR subject_id IS NULL)</if>
             <if test='enabledOnly'>AND enabled=1</if>
             ORDER BY sort_order,id
            </script>
            """)
    List<KnowledgeNodeEntity> selectTreeRows(@Param("stageId") Long stageId, @Param("gradeId") Long gradeId,
            @Param("subjectId") Long subjectId, @Param("enabledOnly") boolean enabledOnly);

    @Update("""
            UPDATE knowledge_node
               SET node_code=#{nodeCode},name=#{name},node_type=#{nodeType},stage_id=#{stageId},grade_id=#{gradeId},
                   subject_id=#{subjectId},sort_order=#{sortOrder},difficulty=#{difficulty},description=#{description},
                   keywords=#{keywords},version=version+1,update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND deleted=0
            """)
    int updateWithVersion(KnowledgeNodeEntity node);

    @Update("""
            UPDATE knowledge_node
               SET parent_id=#{parentId},level_no=#{levelNo},sort_order=#{sortOrder},version=version+1,
                   update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND deleted=0
            """)
    int moveWithVersion(@Param("id") Long id, @Param("parentId") Long parentId,
            @Param("levelNo") Integer levelNo, @Param("sortOrder") Integer sortOrder,
            @Param("version") Integer version);

    @Update("""
            UPDATE knowledge_node
               SET enabled=0,version=version+1,update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND enabled=1 AND deleted=0
            """)
    int disableWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
