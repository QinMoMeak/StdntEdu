package com.stdntedu.ai.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelType;
import com.stdntedu.generated.model.AiProtocol;
import com.stdntedu.generated.model.AiProvider;
import lombok.Data;

@Data
@TableName("ai_model")
public class AiModelEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private AiProvider provider;
    private AiModelType modelType;
    private String modelName;
    private AiProtocol protocol;
    private AiAuthType authType;
    private String apiBaseUrl;
    private String apiKeyRef;
    private Boolean supportsVision;
    private Boolean supportsJson;
    private Boolean localFlag;
    private Boolean enabled;
    private Integer priorityNo;
    private Integer timeoutSeconds;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String remark;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
