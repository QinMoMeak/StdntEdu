package com.stdntedu.resource.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class StudyLogRow {
    private Long id;
    private Long studentId;
    private Long subjectId;
    private String subjectName;
    private LocalDate studyDate;
    private Integer durationSeconds;
    private String content;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
