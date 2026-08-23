package com.stdntedu.backup.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.backup.entity.RestoreRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RestoreRecordMapper extends BaseMapper<RestoreRecordEntity> {
    @Select("SELECT id FROM restore_record WHERE status='PENDING' AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<Long> selectPendingIds(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT id FROM restore_record WHERE status='RUNNING' AND database_applied=1 AND progress_stage='FINALIZING' ORDER BY id LIMIT #{limit}")
    List<Long> selectFinalizingIds(@Param("limit") int limit);
}
