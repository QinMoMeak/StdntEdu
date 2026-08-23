package com.stdntedu.growth.event.mapper;

import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.growth.event.entity.GrowthEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GrowthEventMapper extends BaseMapper<GrowthEventEntity> {
    String VIEW_FROM = """
            FROM growth_event ge
            LEFT JOIN dict_type dt ON dt.dict_code='growth_event_type'
            LEFT JOIN dict_item di ON di.dict_type_id=dt.id AND di.item_code=ge.event_type
            """;

    @Select("SELECT ge.*,di.item_label AS event_type_label " + VIEW_FROM
            + " WHERE ge.id=#{id} AND ge.deleted=0")
    GrowthEventEntity selectViewById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(*) FROM growth_event
             WHERE student_id=#{studentId} AND deleted=0
            <if test='eventType != null'> AND event_type=#{eventType}</if>
            <if test='startDate != null'> AND event_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND event_date &lt;= #{endDate}</if>
            <if test='keyword != null'> AND (title LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%'))</if>
            </script>
            """)
    long countPage(@Param("studentId") Long studentId, @Param("eventType") String eventType,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT ge.*,di.item_label AS event_type_label
            """ + VIEW_FROM + """
             WHERE ge.student_id=#{studentId} AND ge.deleted=0
            <if test='eventType != null'> AND ge.event_type=#{eventType}</if>
            <if test='startDate != null'> AND ge.event_date &gt;= #{startDate}</if>
            <if test='endDate != null'> AND ge.event_date &lt;= #{endDate}</if>
            <if test='keyword != null'> AND (ge.title LIKE CONCAT('%',#{keyword},'%') OR ge.description LIKE CONCAT('%',#{keyword},'%'))</if>
             ORDER BY ge.event_date DESC,ge.create_time DESC,ge.id DESC
             LIMIT #{offset},#{limit}
            </script>
            """)
    List<GrowthEventEntity> selectPage(@Param("studentId") Long studentId,
            @Param("eventType") String eventType, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("keyword") String keyword,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            SELECT di.item_label
              FROM dict_item di JOIN dict_type dt ON dt.id=di.dict_type_id
             WHERE dt.dict_code='growth_event_type' AND dt.enabled=1
               AND di.item_code=#{eventType} AND di.enabled=1
            """)
    String selectEnabledEventTypeLabel(@Param("eventType") String eventType);

    @Update("""
            UPDATE growth_event SET student_id=#{event.studentId},event_type=#{event.eventType},
                   title=#{event.title},event_date=#{event.eventDate},description=#{event.description},
                   tags=#{event.tags},version=version+1
             WHERE id=#{event.id} AND version=#{expectedVersion} AND deleted=0
            """)
    int updateWithVersion(@Param("event") GrowthEventEntity event,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE growth_event SET deleted=1,version=version+1 WHERE id=#{id} AND version=#{version} AND deleted=0")
    int deleteWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
