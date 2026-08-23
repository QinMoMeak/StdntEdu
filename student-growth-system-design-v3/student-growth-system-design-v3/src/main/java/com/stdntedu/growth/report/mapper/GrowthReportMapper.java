package com.stdntedu.growth.report.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.growth.report.entity.GrowthReportEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GrowthReportMapper extends BaseMapper<GrowthReportEntity> {
    @Select("SELECT * FROM growth_report WHERE id=#{id} AND deleted=0 FOR UPDATE")
    GrowthReportEntity selectForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM growth_report WHERE source_report_id=#{sourceId} AND status IN('PENDING','RUNNING') AND deleted=0")
    int countActiveChild(@Param("sourceId") Long sourceId);

    @Select("SELECT id FROM growth_report WHERE status='PENDING' AND deleted=0 AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<Long> selectPendingIds(@Param("afterId") long afterId, @Param("limit") int limit);
}
