package com.travelchart.userservice.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.userservice.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
