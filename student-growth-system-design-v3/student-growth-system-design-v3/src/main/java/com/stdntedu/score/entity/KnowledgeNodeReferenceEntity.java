package com.stdntedu.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Read-only projection used to validate and present score knowledge details. */
@Data
@TableName("knowledge_node")
public class KnowledgeNodeReferenceEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String nodeCode;
    private String name;
    private String nodeType;
    private Long subjectId;
    private Boolean enabled;
    @TableLogic private Boolean deleted;
}
