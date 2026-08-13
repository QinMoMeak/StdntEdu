package com.stdntedu.wrongquestion.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data @TableName("wrong_question")
public class WrongQuestionEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long studentId; private Long subjectId; private Long examId;
 private String sourceType; private String sourceName; private String questionType; private String questionText;
 private String studentAnswer; private String correctAnswer; private String answerSource; private String analysisText;
 private String analysisSource; private String errorType; private Integer difficulty; private String status;
 private Integer reviewStage; private Integer reviewCount; private LocalDateTime lastReviewTime; private LocalDateTime nextReviewTime;
 private LocalDate occurredDate; private String remark; @TableLogic private Boolean deleted; @Version private Integer version;
 private LocalDateTime createTime; private LocalDateTime updateTime;
}
