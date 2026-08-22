package com.stdntedu.ai.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    @Select("""
            SELECT DISTINCT a.*
              FROM attachment a
              JOIN ai_extraction_file f ON f.attachment_id = a.id
             WHERE a.deleted = 0 AND a.storage_type = 'LOCAL'
             ORDER BY a.id
            """)
    List<AttachmentEntity> selectExtractionAttachments();
}
