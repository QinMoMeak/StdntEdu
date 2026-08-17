package com.stdntedu.resource.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.stdntedu.generated.model.StudentResourceStatus;
import lombok.Data;

@Data
@TableName("student_resource_assignment")
public class StudentResourceAssignmentEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long resourceId;
    private StudentResourceStatus status;
    private LocalDateTime assignedTime;
    private String remark;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
