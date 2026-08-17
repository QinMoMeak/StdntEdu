package com.stdntedu.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.resource.entity.StudentResourceAssignmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StudentResourceAssignmentMapper extends BaseMapper<StudentResourceAssignmentEntity> {
    @Update("""
            UPDATE student_resource_assignment
            SET status = #{status}, remark = #{remark}, version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version}
            """)
    int updateWithVersion(@Param("id") Long id, @Param("status") StudentResourceStatus status,
            @Param("remark") String remark, @Param("version") Integer version);

    @Update("""
            UPDATE student_resource_assignment
            SET status = #{targetStatus}, version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version} AND status = #{expectedStatus}
            """)
    int transitionWithVersion(@Param("id") Long id, @Param("version") Integer version,
            @Param("expectedStatus") StudentResourceStatus expectedStatus,
            @Param("targetStatus") StudentResourceStatus targetStatus);
}
