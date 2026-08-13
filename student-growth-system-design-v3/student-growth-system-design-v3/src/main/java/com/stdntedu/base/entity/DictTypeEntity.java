package com.stdntedu.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_type")
public class DictTypeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String dictCode;
    private String dictName;
    private String description;
    private Boolean enabled;
}
