package com.stdntedu.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("learning_resource_knowledge")
public class LearningResourceKnowledgeEntity {
    private Long resourceId;
    private Long knowledgeId;
}
