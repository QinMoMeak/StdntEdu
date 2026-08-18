package com.stdntedu.ai.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_secret")
public class AiSecretEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String secretRef;
    private byte[] encryptedValue;
    private byte[] nonce;
    private String algorithm;
    private Integer keyVersion;
    private String maskSuffix;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
