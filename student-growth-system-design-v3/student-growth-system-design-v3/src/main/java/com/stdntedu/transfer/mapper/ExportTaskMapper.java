package com.stdntedu.transfer.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.transfer.entity.ExportTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTaskEntity> {
    @Select("SELECT id FROM export_task WHERE status=#{status} AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<Long> selectIdsByStatusAfter(@Param("status") String status, @Param("afterId") long afterId,
            @Param("limit") int limit);
}
