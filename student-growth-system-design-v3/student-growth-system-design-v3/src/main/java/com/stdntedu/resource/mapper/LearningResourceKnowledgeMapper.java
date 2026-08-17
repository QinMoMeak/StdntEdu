package com.stdntedu.resource.mapper;

import java.util.List;

import com.stdntedu.resource.entity.LearningResourceKnowledgeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearningResourceKnowledgeMapper {
    @Insert("""
            <script>
            INSERT INTO learning_resource_knowledge(resource_id, knowledge_id) VALUES
            <foreach collection='relations' item='relation' separator=','>
              (#{relation.resourceId}, #{relation.knowledgeId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("relations") List<LearningResourceKnowledgeEntity> relations);

    @Delete("DELETE FROM learning_resource_knowledge WHERE resource_id = #{resourceId}")
    int deleteByResourceId(@Param("resourceId") Long resourceId);

    @Select("""
            SELECT resource_id AS resourceId, knowledge_id AS knowledgeId
            FROM learning_resource_knowledge
            WHERE resource_id = #{resourceId}
            ORDER BY knowledge_id
            """)
    List<LearningResourceKnowledgeEntity> selectByResourceId(@Param("resourceId") Long resourceId);

    @Select("""
            <script>
            SELECT resource_id AS resourceId, knowledge_id AS knowledgeId
            FROM learning_resource_knowledge
            WHERE resource_id IN
            <foreach collection='resourceIds' item='resourceId' open='(' separator=',' close=')'>
              #{resourceId}
            </foreach>
            ORDER BY resource_id, knowledge_id
            </script>
            """)
    List<LearningResourceKnowledgeEntity> selectByResourceIds(@Param("resourceIds") List<Long> resourceIds);
}
