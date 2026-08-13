package com.stdntedu.wrongquestion.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("system_config")
public class SystemConfigReferenceEntity { @TableId private Long id; private String configKey; private String configValue; }
