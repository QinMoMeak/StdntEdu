package com.stdntedu.wrongquestion.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("wrong_question_knowledge")
public class WrongQuestionKnowledgeEntity {
 @TableId(value="wrong_question_id",type=IdType.INPUT) private Long wrongQuestionId; private Long knowledgeId; private Boolean isPrimary; private BigDecimal confidence;
}
