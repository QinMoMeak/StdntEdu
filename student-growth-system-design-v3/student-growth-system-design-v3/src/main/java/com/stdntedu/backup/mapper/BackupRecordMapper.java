package com.stdntedu.backup.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.backup.entity.BackupRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecordEntity> {
    @Select("SELECT id FROM backup_record WHERE status='PENDING' AND deleted=0 AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<Long> selectPendingIds(@Param("afterId") long afterId, @Param("limit") int limit);
}
