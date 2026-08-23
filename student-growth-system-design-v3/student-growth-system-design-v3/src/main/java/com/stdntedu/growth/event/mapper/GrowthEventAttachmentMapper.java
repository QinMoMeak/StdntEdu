package com.stdntedu.growth.event.mapper;

import java.util.List;

import com.stdntedu.growth.event.projection.GrowthEventAttachmentRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GrowthEventAttachmentMapper {
    @Insert("""
            <script>
            INSERT INTO entity_attachment(entity_type,entity_id,attachment_id,attachment_role,sort_order) VALUES
            <foreach collection='attachmentIds' item='attachmentId' index='sortOrder' separator=','>
              (#{entityType},#{eventId},#{attachmentId},#{role},#{sortOrder})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("entityType") String entityType, @Param("eventId") Long eventId,
            @Param("role") String role, @Param("attachmentIds") List<Long> attachmentIds);

    @Delete("DELETE FROM entity_attachment WHERE entity_type=#{entityType} AND entity_id=#{eventId}")
    int deleteByEventId(@Param("entityType") String entityType, @Param("eventId") Long eventId);

    @Select("""
            <script>
            SELECT ea.entity_id AS event_id,a.id AS attachment_id,a.file_name,a.mime_type,
                   a.file_size,a.sha256,a.create_time
              FROM entity_attachment ea JOIN attachment a ON a.id=ea.attachment_id AND a.deleted=0
             WHERE ea.entity_type=#{entityType} AND ea.attachment_role=#{role} AND ea.entity_id IN
            <foreach collection='eventIds' item='eventId' open='(' separator=',' close=')'>#{eventId}</foreach>
             ORDER BY ea.entity_id,ea.sort_order,ea.id
            </script>
            """)
    List<GrowthEventAttachmentRow> selectByEventIds(@Param("entityType") String entityType,
            @Param("role") String role, @Param("eventIds") List<Long> eventIds);
}
