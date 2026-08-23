package com.stdntedu.ai.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    @Select("""
            SELECT *
              FROM attachment
             WHERE deleted = 0 AND storage_type = 'LOCAL'
             ORDER BY id
            """)
    List<AttachmentEntity> selectLocalAttachments();
}
