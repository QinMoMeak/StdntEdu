package com.stdntedu.ai.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.ai.model.entity.AiModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiModelMapper extends BaseMapper<AiModelEntity> {
    @Update("""
            UPDATE ai_model
               SET name=#{model.name}, provider=#{model.provider}, model_type=#{model.modelType},
                   model_name=#{model.modelName}, protocol=#{model.protocol}, auth_type=#{model.authType},
                   api_base_url=#{model.apiBaseUrl}, api_key_ref=#{model.apiKeyRef},
                   supports_vision=#{model.supportsVision}, supports_json=#{model.supportsJson},
                   local_flag=#{model.localFlag}, enabled=#{model.enabled}, priority_no=#{model.priorityNo},
                   timeout_seconds=#{model.timeoutSeconds}, temperature=#{model.temperature},
                   max_tokens=#{model.maxTokens}, remark=#{model.remark},
                   version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{model.id} AND version=#{expectedVersion}
            """)
    int updateWithVersion(@Param("model") AiModelEntity model, @Param("expectedVersion") Integer expectedVersion);

    @Update("""
            UPDATE ai_model
               SET enabled=#{targetEnabled}, version=version+1, update_time=CURRENT_TIMESTAMP(3)
             WHERE id=#{id} AND version=#{version} AND enabled=#{expectedEnabled}
            """)
    int changeEnabledWithVersion(@Param("id") Long id, @Param("version") Integer version,
            @Param("expectedEnabled") boolean expectedEnabled, @Param("targetEnabled") boolean targetEnabled);
}
