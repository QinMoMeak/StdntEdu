package com.stdntedu.transfer.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.transfer.entity.ImportTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImportTaskMapper extends BaseMapper<ImportTaskEntity> {
    @Select("SELECT id FROM import_task WHERE status=#{status} AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<Long> selectIdsByStatusAfter(@Param("status") String status, @Param("afterId") long afterId,
            @Param("limit") int limit);

    @Select("SELECT * FROM import_task WHERE id=#{id} FOR UPDATE")
    ImportTaskEntity selectForUpdate(@Param("id") Long id);
}
