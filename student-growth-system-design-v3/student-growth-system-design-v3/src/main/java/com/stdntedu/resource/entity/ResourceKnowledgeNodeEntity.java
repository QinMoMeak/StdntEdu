package com.stdntedu.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("knowledge_node")
public class ResourceKnowledgeNodeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long subjectId;
    private Boolean enabled;
    @TableLogic private Boolean deleted;
}
