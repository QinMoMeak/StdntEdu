package com.stdntedu.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.student.entity.StudentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentMapper extends BaseMapper<StudentEntity> {
    @Select("SELECT id FROM student WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);
}
