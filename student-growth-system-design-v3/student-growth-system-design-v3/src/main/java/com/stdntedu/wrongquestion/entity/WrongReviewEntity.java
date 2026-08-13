package com.stdntedu.wrongquestion.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data @TableName("wrong_review")
public class WrongReviewEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long wrongQuestionId; private LocalDateTime reviewTime; private String result;
 private BigDecimal score; private Integer durationSeconds; private String studentAnswer; private String remark;
 private LocalDateTime nextReviewTime; private String idempotencyKey; private LocalDateTime createTime;
}
