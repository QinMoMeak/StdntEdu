package com.stdntedu.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("subject")
public class SubjectEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
}
