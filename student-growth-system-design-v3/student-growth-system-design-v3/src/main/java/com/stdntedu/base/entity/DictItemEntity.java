package com.stdntedu.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_item")
public class DictItemEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long dictTypeId;
    private String itemCode;
    private String itemLabel;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean systemFlag;
    private String description;
}
