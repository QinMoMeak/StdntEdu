package com.stdntedu.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stdntedu.student.entity.StudentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper public interface StudentMapper extends BaseMapper<StudentEntity> { }
