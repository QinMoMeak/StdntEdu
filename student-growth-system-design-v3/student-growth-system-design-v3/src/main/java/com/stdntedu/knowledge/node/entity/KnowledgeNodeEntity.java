package com.stdntedu.knowledge.node.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("knowledge_node")
public class KnowledgeNodeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long parentId;
    private String nodeCode;
    private String name;
    private String nodeType;
    private Long stageId;
    private Long gradeId;
    private Long subjectId;
    private Integer levelNo;
    private Integer sortOrder;
    private Integer difficulty;
    private String description;
    private String keywords;
    private Boolean enabled;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
