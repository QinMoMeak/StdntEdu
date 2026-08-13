package com.stdntedu.score.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("score_record")
public class ScoreRecordEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long examId;
    private Long studentId;
    private Long subjectId;
    private BigDecimal score;
    private BigDecimal fullScore;
    private Integer classRank;
    private Integer gradeRank;
    private Integer classSize;
    private Integer gradeSize;
    private String remark;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
