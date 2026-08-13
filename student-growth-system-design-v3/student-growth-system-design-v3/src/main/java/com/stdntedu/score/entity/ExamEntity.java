package com.stdntedu.score.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("exam")
public class ExamEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long academicTermId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private BigDecimal totalScore;
    private BigDecimal totalFullScore;
    private String remark;
    @TableLogic private Boolean deleted;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
